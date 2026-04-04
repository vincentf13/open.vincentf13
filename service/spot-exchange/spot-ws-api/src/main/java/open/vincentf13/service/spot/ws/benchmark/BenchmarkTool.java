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
 * 使用 Netty WebSocket client 以恆定速率發送訂單，
 * 透過 clientOrderId=nanoTime 攜帶發送時間戳，
 * 收到 Execution Report 後計算 round-trip latency，
 * 以 HdrHistogram 記錄延遲分佈。
 *
 * Usage: java -cp ... BenchmarkTool [rate/sec] [duration_sec] [warmup_sec]
 */
public class BenchmarkTool {

    private static final String WS_URL = "ws://127.0.0.1:8080/ws/spot";
    private static final long SCALE = 100_000_000L;

    // SBE 常數
    private static final int MSG_TYPE_AUTH = 103, MSG_TYPE_DEPOSIT = 102, MSG_TYPE_ORDER_CREATE = 100;
    private static final int SBE_SCHEMA_ID = 1, SBE_VERSION = 1;

    // 測試參數
    private static int targetRate = 50_000;   // orders/sec
    private static int durationSec = 30;
    private static int warmupSec = 5;

    // 統計
    private static final Histogram histogram = new Histogram(TimeUnit.SECONDS.toNanos(10), 3);
    private static final AtomicLong sentCount = new AtomicLong();
    private static final AtomicLong recvCount = new AtomicLong();
    private static volatile boolean measuring = false;

    public static void main(String[] args) throws Exception {
        if (args.length >= 1) targetRate = Integer.parseInt(args[0]);
        if (args.length >= 2) durationSec = Integer.parseInt(args[1]);
        if (args.length >= 3) warmupSec = Integer.parseInt(args[2]);

        System.out.printf("Benchmark: rate=%d/sec, duration=%ds, warmup=%ds%n", targetRate, durationSec, warmupSec);

        EventLoopGroup group = new NioEventLoopGroup(2);
        CountDownLatch connected = new CountDownLatch(1);
        Channel[] ch = new Channel[1];

        try {
            URI uri = new URI(WS_URL);
            WebSocketClientHandshaker handshaker = WebSocketClientHandshakerFactory.newHandshaker(
                    uri, WebSocketVersion.V13, null, true, new DefaultHttpHeaders());

            Bootstrap b = new Bootstrap()
                    .group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override protected void initChannel(SocketChannel sc) {
                            sc.pipeline().addLast(
                                    new HttpClientCodec(),
                                    new HttpObjectAggregator(8192),
                                    new BenchmarkHandler(handshaker, connected));
                        }
                    });

            ch[0] = b.connect(uri.getHost(), uri.getPort()).sync().channel();
            if (!connected.await(5, TimeUnit.SECONDS)) {
                System.err.println("WebSocket handshake timeout");
                return;
            }
            System.out.println("Connected to " + WS_URL);

            // 1. Auth + Deposit
            long userId = 9999;
            ch[0].writeAndFlush(frame(encodeAuth(userId)));
            ch[0].writeAndFlush(frame(encodeDeposit(userId, 2, Long.MAX_VALUE / 2))); // USDT
            ch[0].writeAndFlush(frame(encodeDeposit(userId, 1, Long.MAX_VALUE / 2))); // BTC
            Thread.sleep(2000);
            System.out.println("Auth + Deposit done");

            // 2. Pre-allocate order buffer
            byte[] orderTemplate = encodeOrder(userId, 1001, 60000L * SCALE, 1000L, 0, 0);

            // 3. Warmup phase
            System.out.printf("Warmup %ds...%n", warmupSec);
            sendAtRate(ch[0], orderTemplate, userId, targetRate, warmupSec, false);

            // 4. Measurement phase
            histogram.reset();
            sentCount.set(0);
            recvCount.set(0);
            measuring = true;
            System.out.printf("Measuring %ds at %d/sec...%n", durationSec, targetRate);
            sendAtRate(ch[0], orderTemplate, userId, targetRate, durationSec, true);
            measuring = false;

            // 5. Drain remaining reports
            Thread.sleep(2000);

            // 6. Print results
            System.out.println("\n=== Benchmark Results ===");
            System.out.printf("Sent: %d, Received: %d (%.1f%%)%n",
                    sentCount.get(), recvCount.get(),
                    sentCount.get() > 0 ? recvCount.get() * 100.0 / sentCount.get() : 0);
            System.out.printf("Throughput: %d orders/sec%n", sentCount.get() / Math.max(durationSec, 1));
            System.out.println("\nRound-trip Latency Distribution:");
            System.out.printf("  p50  = %,d ns (%.2f ms)%n", histogram.getValueAtPercentile(50), histogram.getValueAtPercentile(50) / 1e6);
            System.out.printf("  p90  = %,d ns (%.2f ms)%n", histogram.getValueAtPercentile(90), histogram.getValueAtPercentile(90) / 1e6);
            System.out.printf("  p99  = %,d ns (%.2f ms)%n", histogram.getValueAtPercentile(99), histogram.getValueAtPercentile(99) / 1e6);
            System.out.printf("  p999 = %,d ns (%.2f ms)%n", histogram.getValueAtPercentile(99.9), histogram.getValueAtPercentile(99.9) / 1e6);
            System.out.printf("  max  = %,d ns (%.2f ms)%n", histogram.getMaxValue(), histogram.getMaxValue() / 1e6);
            System.out.printf("  mean = %,.0f ns (%.2f ms)%n", histogram.getMean(), histogram.getMean() / 1e6);
            System.out.printf("  samples = %d%n", histogram.getTotalCount());

        } finally {
            if (ch[0] != null) ch[0].close();
            group.shutdownGracefully();
        }
    }

    /** 以令牌桶恆定速率發送訂單 */
    private static void sendAtRate(Channel ch, byte[] template, long userId, int rate, int seconds, boolean count) {
        long intervalNs = 1_000_000_000L / rate;
        long totalOrders = (long) rate * seconds;
        long startNs = System.nanoTime();
        long cidCounter = startNs;
        boolean isBuy = true;

        for (long i = 0; i < totalOrders; i++) {
            long sendNano = System.nanoTime();
            // clientOrderId = sendNano (攜帶發送時間戳)
            byte[] order = patchOrder(template, isBuy ? (byte) 0 : (byte) 1, sendNano);
            ch.writeAndFlush(frame(order));
            if (count) sentCount.incrementAndGet();
            isBuy = !isBuy;

            // 令牌桶限速：等待到下一個發送時刻
            long nextSendNs = startNs + (i + 1) * intervalNs;
            while (System.nanoTime() < nextSendNs) {
                Thread.onSpinWait();
            }
        }
    }

    private static BinaryWebSocketFrame frame(byte[] data) {
        return new BinaryWebSocketFrame(Unpooled.wrappedBuffer(data));
    }

    // ========== SBE 編碼 ==========

    private static void writeSbeHeader(ByteBuffer buf, int msgType, int blockLength) {
        buf.putInt(msgType);                    // [0-3] MsgType
        buf.putLong(-1L);                       // [4-11] reserved
        buf.putShort((short) blockLength);      // [12-13] blockLength
        buf.putShort((short) msgType);          // [14-15] templateId
        buf.putShort((short) SBE_SCHEMA_ID);    // [16-17]
        buf.putShort((short) SBE_VERSION);      // [18-19]
    }

    private static byte[] encodeAuth(long userId) {
        ByteBuffer buf = ByteBuffer.allocate(36).order(ByteOrder.LITTLE_ENDIAN);
        writeSbeHeader(buf, MSG_TYPE_AUTH, 16);
        buf.putLong(System.nanoTime());  // timestamp
        buf.putLong(userId);
        return buf.array();
    }

    private static byte[] encodeDeposit(long userId, int assetId, long amount) {
        ByteBuffer buf = ByteBuffer.allocate(48).order(ByteOrder.LITTLE_ENDIAN);
        writeSbeHeader(buf, MSG_TYPE_DEPOSIT, 28);
        buf.putLong(System.nanoTime());
        buf.putLong(userId);
        buf.putInt(assetId);
        buf.putLong(amount);
        return buf.array();
    }

    private static byte[] encodeOrder(long userId, int symbolId, long price, long qty, int side, long cid) {
        ByteBuffer buf = ByteBuffer.allocate(65).order(ByteOrder.LITTLE_ENDIAN);
        writeSbeHeader(buf, MSG_TYPE_ORDER_CREATE, 45);
        buf.putLong(System.nanoTime());  // timestamp [20]
        buf.putLong(userId);              // userId [28]
        buf.putInt(symbolId);             // symbolId [36]
        buf.putLong(price);               // price [40]
        buf.putLong(qty);                 // qty [48]
        buf.put((byte) side);             // side [56]
        buf.putLong(cid);                 // clientOrderId [57]
        return buf.array();
    }

    /** 快速修改模板中的 side 和 clientOrderId */
    private static byte[] patchOrder(byte[] template, byte side, long clientOrderId) {
        byte[] copy = template.clone();
        copy[56] = side;
        // clientOrderId at offset 57 (LE)
        long cid = clientOrderId;
        for (int i = 0; i < 8; i++) { copy[57 + i] = (byte) (cid & 0xFF); cid >>= 8; }
        // timestamp at offset 20 (LE)
        long ts = System.nanoTime();
        for (int i = 0; i < 8; i++) { copy[20 + i] = (byte) (ts & 0xFF); ts >>= 8; }
        return copy;
    }

    // ========== WebSocket Handler ==========

    private static class BenchmarkHandler extends SimpleChannelInboundHandler<Object> {
        private final WebSocketClientHandshaker handshaker;
        private final CountDownLatch connected;

        BenchmarkHandler(WebSocketClientHandshaker handshaker, CountDownLatch connected) {
            this.handshaker = handshaker;
            this.connected = connected;
        }

        @Override public void channelActive(ChannelHandlerContext ctx) { handshaker.handshake(ctx.channel()); }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
            if (!handshaker.isHandshakeComplete()) {
                try { handshaker.finishHandshake(ctx.channel(), (FullHttpResponse) msg); connected.countDown(); }
                catch (Exception e) { System.err.println("Handshake failed: " + e.getMessage()); }
                return;
            }

            if (msg instanceof BinaryWebSocketFrame frame) {
                if (!measuring) return;
                ByteBuf content = frame.content();
                if (content.readableBytes() < 65) return; // 至少需要完整的 report header

                int msgType = content.getIntLE(0);
                // clientOrderId 位置依 msgType 不同：
                //   201 (Accepted): offset 20+24 = 44 (4th field)
                //   202 (Rejected): offset 20+16 = 36 (3rd field)
                //   204 (Matched):  offset 20+57 = 77 (9th field)  -- too far, might exceed frame
                //   203 (Canceled): offset 20+32 = 52 (5th field)
                long clientOrderId = switch (msgType) {
                    case 201 -> content.readableBytes() >= 52 ? content.getLongLE(44) : 0;
                    case 202 -> content.readableBytes() >= 44 ? content.getLongLE(36) : 0;
                    case 203 -> content.readableBytes() >= 60 ? content.getLongLE(52) : 0;
                    case 204 -> content.readableBytes() >= 85 ? content.getLongLE(77) : 0;
                    default -> 0;
                };

                if (clientOrderId > 0) {
                    long roundTrip = System.nanoTime() - clientOrderId;
                    if (roundTrip > 0 && roundTrip < TimeUnit.SECONDS.toNanos(10)) {
                        histogram.recordValue(roundTrip);
                    }
                    recvCount.incrementAndGet();
                }
            }
        }

        @Override public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            System.err.println("Error: " + cause.getMessage());
            ctx.close();
        }
    }
}
