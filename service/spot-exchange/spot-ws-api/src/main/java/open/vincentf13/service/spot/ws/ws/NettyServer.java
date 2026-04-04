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
 */
@Slf4j
@Component
public class NettyServer {
    @Value("${websocket.port:8080}")
    private int port;

    @Value("${netty.worker.count:4}")
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
            bossGroup = new NioEventLoopGroup(1, AffinityUtil.newThreadFactory("netty-boss",
                    new long[]{MetricsKey.CPU_ID_NETTY_BOSS},
                    new long[]{MetricsKey.CPU_ID_CURRENT_NETTY_BOSS}));

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
                            ch.pipeline().addLast(new HttpServerCodec());
                            ch.pipeline().addLast(new ChunkedWriteHandler());
                            ch.pipeline().addLast(new HttpObjectAggregator(65536));
                            ch.pipeline().addLast(new WebSocketServerProtocolHandler(Ws.PATH));
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
