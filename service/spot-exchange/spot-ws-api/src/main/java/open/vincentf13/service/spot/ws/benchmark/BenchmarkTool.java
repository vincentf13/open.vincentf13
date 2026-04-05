package open.vincentf13.service.spot.ws.benchmark;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;
import org.HdrHistogram.Histogram;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 現貨交易所 E2E 延遲基準測試工具
 *
 * 雙用戶模式：buyer(9998) + seller(9999)，交替發送 BUY/SELL，確保撮合。
 * 定期補充餘額防止資產耗盡。
 * clientOrderId = nanoTime 攜帶發送時間戳，收到 report 後計算 round-trip。
 *
 * Usage: java -cp ... BenchmarkTool [rate/sec] [duration_sec] [warmup_sec]
 */
public class BenchmarkTool {

    private static final String WS_URL = "ws://127.0.0.1:8080/ws/spot";
    private static final long SCALE = 100_000_000L;
    private static final int SBE_SCHEMA_ID = 1, SBE_VERSION = 1;

    private static final long BUYER_ID = 9998;
    private static final long SELLER_ID = 9999;
    private static final long DEPOSIT_AMOUNT = 1_000_000_000L * SCALE; // 10 億單位
    private static final int REDEPOSIT_INTERVAL = 100_000; // 每 10 萬筆補充一次

    private static int targetRate = 50_000;
    private static int durationSec = 30;
    private static int warmupSec = 5;


    private static final Histogram histogram = new Histogram(TimeUnit.SECONDS.toNanos(60), 3);
    private static final AtomicLong sentCount = new AtomicLong();
    private static final AtomicLong recvCount = new AtomicLong();
    private static final AtomicLong acceptedCount = new AtomicLong();
    private static final AtomicLong rejectedCount = new AtomicLong();
    private static final AtomicLong matchedCount = new AtomicLong();
    private static final AtomicLong canceledCount = new AtomicLong();
    private static final AtomicLong unknownCount = new AtomicLong();
    private static volatile boolean measuring = false;

    public static void main(String[] args) throws Exception {
        if (args.length >= 1) targetRate = Integer.parseInt(args[0]);
        if (args.length >= 2) durationSec = Integer.parseInt(args[1]);
        if (args.length >= 3) warmupSec = Integer.parseInt(args[2]);

        System.out.printf("Benchmark: rate=%d/sec, duration=%ds, warmup=%ds%n", targetRate, durationSec, warmupSec);
        System.out.printf("Buyer=%d, Seller=%d, redeposit every %d orders%n", BUYER_ID, SELLER_ID, REDEPOSIT_INTERVAL);

        // 單 channel 只需 1 個 NIO worker，靠 PowerShell ProcessorAffinity 做 process-level 限制。
        // 顯式 thread pin 在 Windows + busy-spin 下會與 QPC / scheduler 互動造成不穩，不主動綁。
        EventLoopGroup group = new NioEventLoopGroup(1);
        CountDownLatch connected = new CountDownLatch(1);

        try {
            URI uri = new URI(WS_URL);
            WebSocketClientHandshaker handshaker = WebSocketClientHandshakerFactory.newHandshaker(
                    uri, WebSocketVersion.V13, null, true, new DefaultHttpHeaders());

            Channel ch = new Bootstrap()
                    .group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override protected void initChannel(SocketChannel sc) {
                            sc.pipeline().addLast(
                                    new HttpClientCodec(),
                                    new HttpObjectAggregator(8192),
                                    new ReportHandler(handshaker, connected));
                        }
                    })
                    .connect(uri.getHost(), uri.getPort()).sync().channel();

            if (!connected.await(5, TimeUnit.SECONDS)) {
                System.err.println("WebSocket handshake timeout");
                return;
            }
            System.out.println("Connected to " + WS_URL);

            // 1. Auth + Deposit 雙用戶
            initAccounts(ch);
            Thread.sleep(2000);
            System.out.println("Auth + Deposit done (2 users)");

            // 2. Pre-allocate order buffers (買賣各一，Direct 重用)
            ByteBuffer buyBuf = ByteBuffer.allocateDirect(65).order(ByteOrder.LITTLE_ENDIAN);
            ByteBuffer sellBuf = ByteBuffer.allocateDirect(65).order(ByteOrder.LITTLE_ENDIAN);
            initOrderTemplate(buyBuf, BUYER_ID, 1001, 60000L * SCALE, 1000L);
            initOrderTemplate(sellBuf, SELLER_ID, 1001, 60000L * SCALE, 1000L);

            // 預拷貝到 byte[] 作為 bulk-copy 源，避免每訊息 65 次單 byte 讀寫
            byte[] buyBytes = new byte[65];
            byte[] sellBytes = new byte[65];
            buyBuf.position(0); buyBuf.get(buyBytes);
            sellBuf.position(0); sellBuf.get(sellBytes);

            // 3. Warmup：以量測速率執行，讓 JIT/GC 充分暖機，避免速率跳躍觸發
            //    GC storm 與 JIT 重編譯污染量測結果。
            System.out.printf("Warmup %ds at %,d/sec...%n", warmupSec, targetRate);
            sendAtRate(ch, buyBytes, sellBytes, targetRate, warmupSec, false);

            // 4. Measure
            histogram.reset();
            sentCount.set(0);
            recvCount.set(0);
            measuring = true;
            System.out.printf("Measuring %ds at %d/sec...%n", durationSec, targetRate);
            sendAtRate(ch, buyBytes, sellBytes, targetRate, durationSec, true);
            measuring = false;

            Thread.sleep(3000);

            // 5. Results
            printResults();

        } finally {
            group.shutdownGracefully();
        }
    }

    private static void initAccounts(Channel ch) {
        // Auth 雙用戶
        ch.writeAndFlush(frame(encodeAuth(BUYER_ID)));
        ch.writeAndFlush(frame(encodeAuth(SELLER_ID)));
        // 充值：買家需要 USDT，賣家需要 BTC
        ch.writeAndFlush(frame(encodeDeposit(BUYER_ID, 2, DEPOSIT_AMOUNT)));  // USDT
        ch.writeAndFlush(frame(encodeDeposit(SELLER_ID, 1, DEPOSIT_AMOUNT))); // BTC
    }

    private static void redeposit(Channel ch) {
        ch.write(frame(encodeDeposit(BUYER_ID, 2, DEPOSIT_AMOUNT)));
        ch.writeAndFlush(frame(encodeDeposit(SELLER_ID, 1, DEPOSIT_AMOUNT)));
    }

    /** 令牌桶恆定速率發送，買賣交替，定期補充餘額 */
    private static void sendAtRate(Channel ch, byte[] buyBytes, byte[] sellBytes,
                                   int rate, int seconds, boolean count) {
        long intervalNs = 1_000_000_000L / rate;
        long total = (long) rate * seconds;
        long startNs = System.nanoTime();
        boolean isBuy = true;
        PooledByteBufAllocator alloc = PooledByteBufAllocator.DEFAULT;

        for (long i = 0; i < total; i++) {
            // 定期補充餘額
            if (i > 0 && i % REDEPOSIT_INTERVAL == 0) {
                redeposit(ch);
            }

            long sendNano = System.nanoTime();
            byte[] src = isBuy ? buyBytes : sellBytes;

            // 每條訊息配獨立 ByteBuf（pooled），避免 Netty async send 時底層記憶體被覆蓋
            ByteBuf out = alloc.directBuffer(65, 65);
            out.writeBytes(src);             // bulk memcpy，一次 JNI 呼叫取代 65 次 writeByte
            out.setLongLE(20, sendNano);     // timestamp (LITTLE_ENDIAN，SBE 格式)
            out.setLongLE(57, sendNano);     // clientOrderId = sendNano

            ch.writeAndFlush(new BinaryWebSocketFrame(out));

            if (count) sentCount.incrementAndGet();
            isBuy = !isBuy;

            long nextNs = startNs + (i + 1) * intervalNs;
            while (System.nanoTime() < nextNs) Thread.onSpinWait();
        }
    }

    private static void printResults() {
        System.out.println("\n=== Benchmark Results ===");
        System.out.printf("Sent: %,d | Throughput: %,d orders/sec%n", sentCount.get(), sentCount.get() / Math.max(durationSec, 1));
        System.out.printf("Reports: accepted=%,d, matched=%,d, rejected=%,d, canceled=%,d, unknown=%,d%n",
                acceptedCount.get(), matchedCount.get(), rejectedCount.get(), canceledCount.get(), unknownCount.get());
        System.out.printf("Latency samples (measuring phase): %,d (%.1f%% of sent)%n",
                recvCount.get(), sentCount.get() > 0 ? recvCount.get() * 100.0 / sentCount.get() : 0);
        if (histogram.getTotalCount() == 0) {
            System.out.println("\nNo latency samples (no reports received)");
            return;
        }
        System.out.println("\nRound-trip Latency:");
        System.out.printf("  p50  = %.2f ms%n", histogram.getValueAtPercentile(50) / 1e6);
        System.out.printf("  p90  = %.2f ms%n", histogram.getValueAtPercentile(90) / 1e6);
        System.out.printf("  p99  = %.2f ms%n", histogram.getValueAtPercentile(99) / 1e6);
        System.out.printf("  p999 = %.2f ms%n", histogram.getValueAtPercentile(99.9) / 1e6);
        System.out.printf("  max  = %.2f ms%n", histogram.getMaxValue() / 1e6);
        System.out.printf("  mean = %.2f ms%n", histogram.getMean() / 1e6);
        System.out.printf("  samples = %d%n", histogram.getTotalCount());
    }

    // ========== SBE encoding ==========

    private static void writeSbeHeader(ByteBuffer buf, int msgType, int blockLength) {
        buf.putInt(msgType);
        buf.putLong(-1L);
        buf.putShort((short) blockLength);
        buf.putShort((short) msgType);
        buf.putShort((short) SBE_SCHEMA_ID);
        buf.putShort((short) SBE_VERSION);
    }

    private static void initOrderTemplate(ByteBuffer buf, long userId, int symbolId, long price, long qty) {
        buf.clear();
        writeSbeHeader(buf, 100, 45);
        buf.putLong(0L);         // [20] timestamp
        buf.putLong(userId);     // [28] userId (固定，不需每次修改)
        buf.putInt(symbolId);    // [36]
        buf.putLong(price);      // [40]
        buf.putLong(qty);        // [48]
        buf.put(userId == BUYER_ID ? (byte) 0 : (byte) 1); // [56] side 固定
        buf.putLong(0L);         // [57] clientOrderId
        buf.flip();
    }

    private static byte[] encodeAuth(long userId) {
        ByteBuffer buf = ByteBuffer.allocate(36).order(ByteOrder.LITTLE_ENDIAN);
        writeSbeHeader(buf, 103, 16);
        buf.putLong(System.nanoTime());
        buf.putLong(userId);
        return buf.array();
    }

    private static byte[] encodeDeposit(long userId, int assetId, long amount) {
        ByteBuffer buf = ByteBuffer.allocate(48).order(ByteOrder.LITTLE_ENDIAN);
        writeSbeHeader(buf, 102, 28);
        buf.putLong(System.nanoTime());
        buf.putLong(userId);
        buf.putInt(assetId);
        buf.putLong(amount);
        return buf.array();
    }

    private static BinaryWebSocketFrame frame(byte[] data) {
        return new BinaryWebSocketFrame(Unpooled.wrappedBuffer(data));
    }

    // ========== Report Handler ==========

    private static class ReportHandler extends SimpleChannelInboundHandler<Object> {
        private static final int ACCEPTED = 107, REJECTED = 108, CANCELED = 109, MATCHED = 110;

        private final WebSocketClientHandshaker handshaker;
        private final CountDownLatch connected;

        ReportHandler(WebSocketClientHandshaker handshaker, CountDownLatch connected) {
            this.handshaker = handshaker;
            this.connected = connected;
        }

        @Override public void channelActive(ChannelHandlerContext ctx) { handshaker.handshake(ctx.channel()); }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
            if (!handshaker.isHandshakeComplete()) {
                try {
                    handshaker.finishHandshake(ctx.channel(), (FullHttpResponse) msg);
                    connected.countDown();
                } catch (Exception e) { System.err.println("Handshake failed: " + e.getMessage()); }
                return;
            }

            if (msg instanceof BinaryWebSocketFrame frame) {
                ByteBuf content = frame.content();
                if (content.readableBytes() < 20) return;

                int msgType = content.getIntLE(0);

                switch (msgType) {
                    case ACCEPTED -> acceptedCount.incrementAndGet();
                    case REJECTED -> rejectedCount.incrementAndGet();
                    case CANCELED -> canceledCount.incrementAndGet();
                    case MATCHED  -> matchedCount.incrementAndGet();
                    default -> { unknownCount.incrementAndGet(); return; }
                }

                if (!measuring) return;

                long clientOrderId = extractClientOrderId(msgType, content);
                if (clientOrderId > 0) {
                    long roundTrip = System.nanoTime() - clientOrderId;
                    if (roundTrip > 0 && roundTrip < TimeUnit.SECONDS.toNanos(60)) {
                        histogram.recordValue(roundTrip);
                    }
                    recvCount.incrementAndGet();
                }
            }
        }

        private long extractClientOrderId(int msgType, ByteBuf buf) {
            return switch (msgType) {
                case ACCEPTED -> buf.readableBytes() >= 52 ? buf.getLongLE(44) : 0;
                case REJECTED -> buf.readableBytes() >= 44 ? buf.getLongLE(36) : 0;
                case CANCELED -> buf.readableBytes() >= 60 ? buf.getLongLE(52) : 0;
                case MATCHED  -> buf.readableBytes() >= 85 ? buf.getLongLE(77) : 0;
                default -> 0;
            };
        }

        @Override public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            System.err.println("WS Error: " + cause.getMessage());
        }
    }
}
