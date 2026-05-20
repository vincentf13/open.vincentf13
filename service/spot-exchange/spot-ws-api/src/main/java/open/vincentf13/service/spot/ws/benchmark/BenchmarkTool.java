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
import org.HdrHistogram.ConcurrentHistogram;
import org.HdrHistogram.Histogram;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

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

    // 連線 i 使用 (BUYER_ID_BASE+2i, BUYER_ID_BASE+2i+1)。i=0 時為 (9998, 9999) 與單連線歷史一致。
    private static final long BUYER_ID_BASE = 9998;
    private static final long DEPOSIT_AMOUNT = 1_000_000_000L * SCALE; // 10 億單位
    private static final int REDEPOSIT_INTERVAL = 100_000; // 每 10 萬筆補充一次

    private static int targetRate = 50_000;
    private static int durationSec = 30;
    private static int warmupSec = 5;
    private static int connections = 1;


    // ConcurrentHistogram：多條 Netty EventLoop 同時 recordValue 安全且 lock-free
    private static final Histogram histogram = new ConcurrentHistogram(TimeUnit.SECONDS.toNanos(60), 3);
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
        if (args.length >= 4) connections = Math.max(1, Integer.parseInt(args[3]));

        int ratePerConn = targetRate / connections;
        System.out.printf("Benchmark: rate=%d/sec, duration=%ds, warmup=%ds, connections=%d (%d/sec each)%n",
                targetRate, durationSec, warmupSec, connections, ratePerConn);
        System.out.printf("User pairs: buyer=%d..%d / seller=%d..%d, redeposit every %d orders%n",
                BUYER_ID_BASE, BUYER_ID_BASE + 2 * (connections - 1),
                BUYER_ID_BASE + 1, BUYER_ID_BASE + 1 + 2 * (connections - 1), REDEPOSIT_INTERVAL);

        // 共享 EventLoopGroup：N 條 channel 自動 round-robin 到 N 個 worker thread，
        // 達成多條 connection 並行 read/decode 與並行 server-side flush。
        EventLoopGroup group = new NioEventLoopGroup(connections);
        List<Channel> channels = new ArrayList<>(connections);

        try {
            URI uri = new URI(WS_URL);

            for (int i = 0; i < connections; i++) {
                CountDownLatch connected = new CountDownLatch(1);
                final WebSocketClientHandshaker hs = WebSocketClientHandshakerFactory.newHandshaker(
                        uri, WebSocketVersion.V13, null, true, new DefaultHttpHeaders(), 256 * 1024);

                Channel ch = new Bootstrap()
                        .group(group)
                        .channel(NioSocketChannel.class)
                        .option(ChannelOption.TCP_NODELAY, true)
                        .handler(new ChannelInitializer<SocketChannel>() {
                            @Override protected void initChannel(SocketChannel sc) {
                                sc.pipeline().addLast(
                                        new HttpClientCodec(),
                                        new HttpObjectAggregator(8192),
                                        new ReportHandler(hs, connected));
                            }
                        })
                        .connect(uri.getHost(), uri.getPort()).sync().channel();

                if (!connected.await(5, TimeUnit.SECONDS)) {
                    System.err.println("WebSocket handshake timeout on conn " + i);
                    return;
                }
                channels.add(ch);
            }
            System.out.printf("Connected %d ws sessions to %s%n", connections, WS_URL);

            // 1. Auth + Deposit：每條連線一對獨立 user（避免報告 fan-out 衝撞）
            for (int i = 0; i < connections; i++) {
                long buyerId = BUYER_ID_BASE + 2L * i;
                long sellerId = buyerId + 1;
                initAccounts(channels.get(i), buyerId, sellerId);
            }
            Thread.sleep(2000);
            System.out.printf("Auth + Deposit done (%d user pairs)%n", connections);

            // 2. 每連線各自啟 sender thread，獨立 token-bucket 不互相干擾
            CountDownLatch warmupDone = new CountDownLatch(connections);
            CountDownLatch measureReady = new CountDownLatch(connections);   // 各 sender 報到
            CountDownLatch measureGo = new CountDownLatch(1);                // 主執行緒一次釋放
            CountDownLatch measureDone = new CountDownLatch(connections);
            long[] sharedStartNs = new long[1];                              // 共享 measure 起跑點

            for (int i = 0; i < connections; i++) {
                final int idx = i;
                final Channel ch = channels.get(i);
                final long buyerId = BUYER_ID_BASE + 2L * idx;
                final long sellerId = buyerId + 1;

                Thread t = new Thread(() -> {
                    ByteBuffer buyBuf = ByteBuffer.allocateDirect(65).order(ByteOrder.LITTLE_ENDIAN);
                    ByteBuffer sellBuf = ByteBuffer.allocateDirect(65).order(ByteOrder.LITTLE_ENDIAN);
                    initOrderTemplate(buyBuf, buyerId, 1001, 60000L * SCALE, 1000L, (byte) 0);
                    initOrderTemplate(sellBuf, sellerId, 1001, 60000L * SCALE, 1000L, (byte) 1);
                    byte[] buyBytes = new byte[65];
                    byte[] sellBytes = new byte[65];
                    buyBuf.position(0); buyBuf.get(buyBytes);
                    sellBuf.position(0); sellBuf.get(sellBytes);

                    // Warmup：自己內部 nanoTime 起跑，互不協調沒關係
                    sendAtRate(ch, buyBytes, sellBytes, ratePerConn, warmupSec, false, buyerId, sellerId, -1L);
                    warmupDone.countDown();

                    // 報到 + 等待主執行緒對齊起跑時間，杜絕各 sender 起點漂移幾百 μs
                    measureReady.countDown();
                    try { measureGo.await(); } catch (InterruptedException ignored) { return; }
                    sendAtRate(ch, buyBytes, sellBytes, ratePerConn, durationSec, true, buyerId, sellerId, sharedStartNs[0]);
                    measureDone.countDown();
                }, "bench-sender-" + idx);
                t.setDaemon(false);
                t.start();
            }

            // 等所有連線完成 warmup → 重置 → 對齊 measure 起跑點
            warmupDone.await();
            measureReady.await();
            System.out.printf("All %d connections warmed up%n", connections);
            histogram.reset();
            sentCount.set(0);
            recvCount.set(0);
            measuring = true;
            System.out.printf("Measuring %ds at %d/sec (%d conns × %d/sec)...%n",
                    durationSec, targetRate, connections, ratePerConn);
            // 給 OS 一個 schedule 機會讓所有 sender 都被喚醒到 spin 上，再對齊起跑
            sharedStartNs[0] = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(5);
            measureGo.countDown();

            measureDone.await();
            measuring = false;

            Thread.sleep(3000);

            printResults();

        } finally {
            group.shutdownGracefully();
        }
    }

    private static void initAccounts(Channel ch, long buyerId, long sellerId) {
        ch.writeAndFlush(frame(encodeAuth(buyerId)));
        ch.writeAndFlush(frame(encodeAuth(sellerId)));
        ch.writeAndFlush(frame(encodeDeposit(buyerId, 2, DEPOSIT_AMOUNT)));  // USDT
        ch.writeAndFlush(frame(encodeDeposit(sellerId, 1, DEPOSIT_AMOUNT))); // BTC
    }

    private static void redeposit(Channel ch, long buyerId, long sellerId) {
        ch.write(frame(encodeDeposit(buyerId, 2, DEPOSIT_AMOUNT)));
        ch.writeAndFlush(frame(encodeDeposit(sellerId, 1, DEPOSIT_AMOUNT)));
    }

    /** 令牌桶恆定速率發送，買賣交替，定期補充餘額；alignedStartNs<0 表示由本函式自己 nanoTime() 起跑 */
    private static void sendAtRate(Channel ch, byte[] buyBytes, byte[] sellBytes,
                                   int rate, int seconds, boolean count,
                                   long buyerId, long sellerId, long alignedStartNs) {
        long intervalNs = 1_000_000_000L / rate;
        long total = (long) rate * seconds;
        long startNs = alignedStartNs > 0 ? alignedStartNs : System.nanoTime();
        // 等到對齊的 start 時間（多 sender 同步起跑）
        if (alignedStartNs > 0) {
            while (System.nanoTime() < startNs) Thread.onSpinWait();
        }
        boolean isBuy = true;
        PooledByteBufAllocator alloc = PooledByteBufAllocator.DEFAULT;

        for (long i = 0; i < total; i++) {
            if (i > 0 && i % REDEPOSIT_INTERVAL == 0) {
                redeposit(ch, buyerId, sellerId);
            }

            long sendNano = System.nanoTime();
            byte[] src = isBuy ? buyBytes : sellBytes;

            ByteBuf out = alloc.directBuffer(65, 65);
            out.writeBytes(src);
            out.setLongLE(20, sendNano);
            out.setLongLE(57, sendNano);

            ch.writeAndFlush(new BinaryWebSocketFrame(out));

            if (count) sentCount.incrementAndGet();
            isBuy = !isBuy;

            long nextNs = startNs + (i + 1) * intervalNs;
            // 混合等待：剩餘 > 10μs 用 parkNanos 讓 CPU 給 NIO worker（多 sender 場景必要），
            // 剩餘 ≤ 10μs 用 onSpinWait 保證 token bucket 精度
            long remaining;
            while ((remaining = nextNs - System.nanoTime()) > 10_000L) {
                LockSupport.parkNanos(remaining - 10_000L);
            }
            while (System.nanoTime() < nextNs) Thread.onSpinWait();
        }
    }

    private static void printResults() {
        System.out.println("\n=== Benchmark Results ===");
        System.out.printf("Sent: %,d | Throughput: %,d orders/sec | Connections: %d%n",
                sentCount.get(), sentCount.get() / Math.max(durationSec, 1), connections);
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

    private static void initOrderTemplate(ByteBuffer buf, long userId, int symbolId, long price, long qty, byte side) {
        buf.clear();
        writeSbeHeader(buf, 100, 45);
        buf.putLong(0L);         // [20] timestamp
        buf.putLong(userId);     // [28] userId (固定，不需每次修改)
        buf.putInt(symbolId);    // [36]
        buf.putLong(price);      // [40]
        buf.putLong(qty);        // [48]
        buf.put(side);           // [56] side (0=BUY, 1=SELL)
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
        private static final int ACCEPTED_LEN = 52, REJECTED_LEN = 44, CANCELED_LEN = 60, MATCHED_LEN = 85;

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
                int readable = content.readableBytes();
                int pos = 0;

                // 支援 batched reports：一個 WebSocket frame 可能包含多筆 report
                while (pos + 20 <= readable) {
                    int msgType = content.getIntLE(pos);
                    int reportLen = reportLength(msgType);
                    if (reportLen <= 0 || pos + reportLen > readable) {
                        unknownCount.incrementAndGet();
                        break;
                    }

                    switch (msgType) {
                        case ACCEPTED -> acceptedCount.incrementAndGet();
                        case REJECTED -> rejectedCount.incrementAndGet();
                        case CANCELED -> canceledCount.incrementAndGet();
                        case MATCHED  -> matchedCount.incrementAndGet();
                    }

                    if (measuring) {
                        long clientOrderId = extractClientOrderId(msgType, content, pos);
                        if (clientOrderId > 0) {
                            long roundTrip = System.nanoTime() - clientOrderId;
                            if (roundTrip > 0 && roundTrip < TimeUnit.SECONDS.toNanos(60)) {
                                histogram.recordValue(roundTrip);
                            }
                            recvCount.incrementAndGet();
                        }
                    }
                    pos += reportLen;
                }
            }
        }

        private static int reportLength(int msgType) {
            return switch (msgType) {
                case ACCEPTED -> ACCEPTED_LEN;
                case REJECTED -> REJECTED_LEN;
                case CANCELED -> CANCELED_LEN;
                case MATCHED  -> MATCHED_LEN;
                default -> -1;
            };
        }

        private static long extractClientOrderId(int msgType, ByteBuf buf, int pos) {
            return switch (msgType) {
                case ACCEPTED -> buf.getLongLE(pos + 44);
                case REJECTED -> buf.getLongLE(pos + 36);
                case CANCELED -> buf.getLongLE(pos + 52);
                case MATCHED  -> buf.getLongLE(pos + 77);
                default -> 0;
            };
        }

        @Override public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            System.err.println("WS Error: " + cause.getMessage());
        }
    }
}
