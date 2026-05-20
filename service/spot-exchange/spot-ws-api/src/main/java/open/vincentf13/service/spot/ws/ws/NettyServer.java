package open.vincentf13.service.spot.ws.ws;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import open.vincentf13.service.spot.infra.Constants.MetricsKey;
import open.vincentf13.service.spot.infra.Constants.Ws;
import open.vincentf13.service.spot.infra.thread.AffinityUtil;
import open.vincentf13.service.spot.infra.metrics.StaticMetricsHolder;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Netty WebSocket 伺服器
 * 職責：啟動/關閉 Netty、配置 Pipeline、綁定 CPU 親和性。
 *
 * @DependsOn 強制這兩個 busy-spin Worker 先 @PostConstruct 完成（含 acquireAndBind 阻塞），
 * 確保 spot.affinity.cores pool 的前兩個 P-core slot 一定給 gateway-sender / gateway-receiver，
 * 而不是被本類的 4 個 netty worker thread 搶先。歷史 bug：實測曾發生 gateway-receiver 落到 E2
 * 而 netty-worker-3 拿到 P10，端到端 p99 從 13ms 飆到 25ms。
 */
@Slf4j
@Component
@DependsOn({"gatewaySender", "reportReceiver"})
public class NettyServer {
    @Value("${websocket.port:8080}")
    private int port;

    @Value("${netty.worker.count:2}")
    private int workerCount;

    private final WsCommandInboundHandler wsHandler;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private final AtomicBoolean started = new AtomicBoolean(false);

    public NettyServer(WsCommandInboundHandler wsHandler) {
        this.wsHandler = wsHandler;
    }

    @PostConstruct
    public void start() {
        if (!started.compareAndSet(false, true)) return;

        new Thread(() -> {
            // bossGroup 處理連線接收，壓力小，不綁定核心
            bossGroup = new NioEventLoopGroup(1, (java.util.concurrent.ThreadFactory) r -> new Thread(r, "netty-boss"));

            // workerGroup 處理命令編解碼與業務邏輯，維持核心綁定
            // chooserFactory：保留 Netty 預設 round-robin（PowerOfTwoEventExecutorChooser 當 N=2^k，否則
            // GenericEventExecutorChooser），第 1 條連線必落 workerGroup[0]。我們的 affinity pool 第 3 順位
            // 是 P-core（spot.affinity.cores 第 3 個元素），因此 workerGroup[0] = P-core netty worker。
            // 這保證「第一條建立的 WebSocket 連線」走最低延遲路徑；後續連線依序 round-robin 到 E-core worker。
            workerGroup = new NioEventLoopGroup(workerCount, AffinityUtil.newThreadFactory("netty-worker",
                    new long[]{MetricsKey.CPU_ID_NETTY_WORKER_1, MetricsKey.CPU_ID_NETTY_WORKER_2,
                               MetricsKey.CPU_ID_NETTY_WORKER_3, MetricsKey.CPU_ID_NETTY_WORKER_4},
                    new long[]{MetricsKey.CPU_ID_CURRENT_NETTY_WORKER_1, MetricsKey.CPU_ID_CURRENT_NETTY_WORKER_2,
                               MetricsKey.CPU_ID_CURRENT_NETTY_WORKER_3, MetricsKey.CPU_ID_CURRENT_NETTY_WORKER_4}));

            scheduleCpuMetrics(bossGroup,
                    new long[]{MetricsKey.CPU_ID_NETTY_BOSS},
                    new long[]{MetricsKey.CPU_ID_CURRENT_NETTY_BOSS});
            scheduleCpuMetrics(workerGroup,
                    new long[]{MetricsKey.CPU_ID_NETTY_WORKER_1, MetricsKey.CPU_ID_NETTY_WORKER_2,
                               MetricsKey.CPU_ID_NETTY_WORKER_3, MetricsKey.CPU_ID_NETTY_WORKER_4},
                    new long[]{MetricsKey.CPU_ID_CURRENT_NETTY_WORKER_1, MetricsKey.CPU_ID_CURRENT_NETTY_WORKER_2,
                               MetricsKey.CPU_ID_CURRENT_NETTY_WORKER_3, MetricsKey.CPU_ID_CURRENT_NETTY_WORKER_4});

            try {
                new ServerBootstrap()
                    .group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 8192)
                    .option(ChannelOption.SO_REUSEADDR, true)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override protected void initChannel(SocketChannel ch) {
                            // 紀錄 channel→worker 對應，事後可從 gw stdout 驗證 round-robin 結果
                            // (channel id 順序對應 connection 建立順序；worker thread name 已包含核心 id)
                            log.info("[WS] channel {} bound to {}",
                                    ch.id().asShortText(), Thread.currentThread().getName());
                            ch.pipeline().addLast(new HttpServerCodec());
                            ch.pipeline().addLast(new ChunkedWriteHandler());
                            ch.pipeline().addLast(new HttpObjectAggregator(65536));
                            ch.pipeline().addLast(new WebSocketServerProtocolHandler(Ws.PATH, null, true, 256 * 1024));
                            ch.pipeline().addLast(wsHandler);
                        }
                    })
                    .bind(port).sync().channel().closeFuture().sync();
                log.info("Netty WebSocket Server started on port {}", port);
            } catch (Exception e) {
                log.error("Netty Server Error", e);
            } finally {
                stop();
            }
        }, "netty-server-starter").start();
    }

    @PreDestroy
    public void stop() {
        if (!started.compareAndSet(true, false)) return;
        shutdownGroup(bossGroup);
        shutdownGroup(workerGroup);
    }

    private void shutdownGroup(EventLoopGroup group) {
        if (group != null) {
            try { group.shutdownGracefully().syncUninterruptibly(); }
            catch (Exception e) { log.warn("Netty group shutdown: {}", e.getMessage()); }
        }
    }

    private void scheduleCpuMetrics(EventLoopGroup group, long[] historyKeys, long[] currentKeys) {
        int i = 0;
        for (var executor : group) {
            if (i >= historyKeys.length) break;
            final long hk = historyKeys[i], ck = currentKeys[i];
            executor.scheduleAtFixedRate(() -> StaticMetricsHolder.recordCpuId(hk, ck, AffinityUtil.currentCpu()), 1, 1, TimeUnit.SECONDS);
            i++;
        }
    }
}
