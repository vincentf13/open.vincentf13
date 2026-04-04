package open.vincentf13.service.spot.ws.benchmark;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
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
 * clientOrderId = nanoTime 攜帶發送時間戳，收到 report 後計算 round-trip。
 * 使用預分配 DirectByteBuffer 重用，避免 GC 壓力。
 *
 * Usage: java -cp ... BenchmarkTool [rate/sec] [duration_sec] [warmup_sec]
 */
public class BenchmarkTool {

    private static final String WS_URL = "ws://127.0.0.1:8080/ws/spot";
    private static final long SCALE = 100_000_000L;
    private static final int SBE_SCHEMA_ID = 1, SBE_VERSION = 1;

    private static int targetRate = 50_000;
    private static int durationSec = 30;
    private static int warmupSec = 5;

    private static final Histogram histogram = new Histogram(TimeUnit.SECONDS.toNanos(10), 3);
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

        EventLoopGroup group = new NioEventLoopGroup(2);
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

            // 1. Auth + Deposit (用 heap buffer，只跑一次)
            long userId = 9999;
            ch.writeAndFlush(frame(encodeAuth(userId)));
            ch.writeAndFlush(frame(encodeDeposit(userId, 2, Long.MAX_VALUE / 2)));
            ch.writeAndFlush(frame(encodeDeposit(userId, 1, Long.MAX_VALUE / 2)));
            Thread.sleep(2000);
            System.out.println("Auth + Deposit done");

            // 2. Pre-allocate order buffer (Direct, 重用)
            ByteBuffer orderBuf = ByteBuffer.allocateDirect(65).order(ByteOrder.LITTLE_ENDIAN);
            initOrderTemplate(orderBuf, userId, 1001, 60000L * SCALE, 1000L);

            // 3. Warmup
            System.out.printf("Warmup %ds...%n", warmupSec);
            sendAtRate(ch, orderBuf, targetRate, warmupSec, false);

            // 4. Measure
            histogram.reset();
            sentCount.set(0);
            recvCount.set(0);
            measuring = true;
            System.out.printf("Measuring %ds at %d/sec...%n", durationSec, targetRate);
            sendAtRate(ch, orderBuf, targetRate, durationSec, true);
            measuring = false;

            Thread.sleep(3000); // drain remaining reports

            // 5. Results
            printResults();

        } finally {
            group.shutdownGracefully();
        }
    }

    /** 令牌桶恆定速率發送，重用 DirectByteBuffer */
    private static void sendAtRate(Channel ch, ByteBuffer template, int rate, int seconds, boolean count) {
        long intervalNs = 1_000_000_000L / rate;
        long total = (long) rate * seconds;
        long startNs = System.nanoTime();
        boolean isBuy = true;

        for (long i = 0; i < total; i++) {
            long sendNano = System.nanoTime();

            // 修改模板中的動態欄位 (zero-copy, 直接寫 DirectByteBuffer)
            template.putLong(20, sendNano);              // timestamp
            template.put(56, isBuy ? (byte) 0 : (byte) 1); // side
            template.putLong(57, sendNano);               // clientOrderId = sendNano

            // 包裝為 Netty frame 發送（wrappedBuffer 不拷貝，共享 direct memory）
            ch.writeAndFlush(new BinaryWebSocketFrame(
                    Unpooled.wrappedBuffer(template.duplicate().position(0).limit(65))));

            if (count) sentCount.incrementAndGet();
            isBuy = !isBuy;

            // 令牌桶：busy-spin 等到下一個時刻
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

    // ========== SBE encoding (init only, not hot path) ==========

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
        writeSbeHeader(buf, 100, 45);      // ORDER_CREATE
        buf.putLong(0L);                    // [20] timestamp (patched per send)
        buf.putLong(userId);                // [28] userId
        buf.putInt(symbolId);               // [36] symbolId
        buf.putLong(price);                 // [40] price
        buf.putLong(qty);                   // [48] qty
        buf.put((byte) 0);                  // [56] side (patched per send)
        buf.putLong(0L);                    // [57] clientOrderId (patched per send)
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

    /**
     * 回報處理器：解碼 report，提取 clientOrderId 計算 round-trip。
     *
     * Report frame: [0-3] msgType (Constants.MsgType) | [4-11] reserved | [12-19] SBE header | [20+] SBE body
     * msgType 使用 Constants.MsgType 值 (107/108/109/110)，非 SBE templateId (201-204)。
     *
     * clientOrderId 在 SBE body 中的位置 (absolute = 20 + SBE offset)：
     *   107 (Accepted):  SBE offset 24 → absolute 44
     *   108 (Rejected):  SBE offset 16 → absolute 36
     *   109 (Canceled):  SBE offset 32 → absolute 52
     *   110 (Matched):   SBE offset 57 → absolute 77
     */
    private static class ReportHandler extends SimpleChannelInboundHandler<Object> {
        // Constants.MsgType report codes
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
                    if (roundTrip > 0 && roundTrip < TimeUnit.SECONDS.toNanos(10)) {
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
