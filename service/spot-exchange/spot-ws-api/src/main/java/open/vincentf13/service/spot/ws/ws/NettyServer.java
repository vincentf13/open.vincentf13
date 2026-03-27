package open.vincentf13.service.spot.ws.ws;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
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

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

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
    private Thread serverThread;

    public NettyServer(WsCommandInboundHandler wsHandler) {
        this.wsHandler = wsHandler;
    }

    @PostConstruct
    public void start() {
        if (!started.compareAndSet(false, true)) return;

        serverThread = new Thread(() -> {
            bossGroup = new NioEventLoopGroup(1, new AffinityThreadFactory("netty-boss", MetricsKey.CPU_ID_NETTY_BOSS));
            workerGroup = new NioEventLoopGroup(workerCount, new AffinityThreadFactory("netty-worker",
                MetricsKey.CPU_ID_NETTY_WORKER_1, MetricsKey.CPU_ID_NETTY_WORKER_2,
                MetricsKey.CPU_ID_NETTY_WORKER_3, MetricsKey.CPU_ID_NETTY_WORKER_4));

            scheduleMetrics(bossGroup,
                new long[]{MetricsKey.CPU_ID_NETTY_BOSS},
                new long[]{MetricsKey.CPU_ID_CURRENT_NETTY_BOSS});
            scheduleMetrics(workerGroup, 
                new long[]{
                    MetricsKey.CPU_ID_NETTY_WORKER_1, MetricsKey.CPU_ID_NETTY_WORKER_2,
                    MetricsKey.CPU_ID_NETTY_WORKER_3, MetricsKey.CPU_ID_NETTY_WORKER_4
                },
                new long[]{
                    MetricsKey.CPU_ID_CURRENT_NETTY_WORKER_1, MetricsKey.CPU_ID_CURRENT_NETTY_WORKER_2,
                    MetricsKey.CPU_ID_CURRENT_NETTY_WORKER_3, MetricsKey.CPU_ID_CURRENT_NETTY_WORKER_4
                });

            try {
                ServerBootstrap b = new ServerBootstrap();
                b.group(bossGroup, workerGroup)
                 .channel(NioServerSocketChannel.class)
                 .option(io.netty.channel.ChannelOption.SO_BACKLOG, 8192)
                 .option(io.netty.channel.ChannelOption.SO_REUSEADDR, true)
                 .childOption(io.netty.channel.ChannelOption.TCP_NODELAY, true)
                 .childOption(io.netty.channel.ChannelOption.SO_KEEPALIVE, true)
                 .childOption(io.netty.channel.ChannelOption.ALLOCATOR, io.netty.buffer.PooledByteBufAllocator.DEFAULT)
                 .childHandler(new ChannelInitializer<SocketChannel>() {
                     @Override
                     protected void initChannel(SocketChannel ch) {
                         ch.pipeline().addLast(new HttpServerCodec());
                         ch.pipeline().addLast(new ChunkedWriteHandler());
                         ch.pipeline().addLast(new HttpObjectAggregator(65536));
                         ch.pipeline().addLast(new WebSocketServerProtocolHandler(Ws.PATH));
                         ch.pipeline().addLast(wsHandler);
                     }
                 });

                log.info("Netty WebSocket Server started on port {}", port);
                b.bind(port).sync().channel().closeFuture().sync();
            } catch (Exception e) {
                log.error("Netty Server Error", e);
            } finally {
                stop();
            }
        }, "netty-server-starter");
        serverThread.start();
    }

    @PreDestroy
    public void stop() {
        if (!started.compareAndSet(true, false)) return;
        shutdownGroup(bossGroup);
        shutdownGroup(workerGroup);
    }

    private void shutdownGroup(EventLoopGroup group) {
        if (group == null) return;
        try {
            group.shutdownGracefully().syncUninterruptibly();
        } catch (Exception e) {
            log.warn("Netty group shutdown failed: {}", e.getMessage());
        }
    }

    private void scheduleMetrics(EventLoopGroup group, long[] historyKeys, long[] currentKeys) {
        int i = 0;
        for (io.netty.util.concurrent.EventExecutor executor : group) {
            if (i >= historyKeys.length || i >= currentKeys.length) break;
            final long historyKey = historyKeys[i];
            final long currentKey = currentKeys[i];
            i++;
            executor.scheduleAtFixedRate(() ->
                StaticMetricsHolder.recordCpuId(historyKey, currentKey, AffinityUtil.currentCpu()),
            1, 1, TimeUnit.SECONDS);
        }
    }

    private static class AffinityThreadFactory implements ThreadFactory {
        private final String prefix;
        private final long[] metricKeys;
        private final AtomicInteger counter = new AtomicInteger(0);

        public AffinityThreadFactory(String prefix, long... metricKeys) {
            this.prefix = prefix;
            this.metricKeys = metricKeys;
        }

        @Override
        public Thread newThread(Runnable r) {
            int currentIdx = counter.getAndIncrement();
            Runnable bindingTask = () -> {
                try {
                    int cpuId = AffinityUtil.acquireAndBind();
                    if (metricKeys != null && currentIdx < metricKeys.length) {
                        long currentMetricKey;
                        if (metricKeys[currentIdx] == MetricsKey.CPU_ID_NETTY_BOSS) currentMetricKey = MetricsKey.CPU_ID_CURRENT_NETTY_BOSS;
                        else if (metricKeys[currentIdx] == MetricsKey.CPU_ID_NETTY_WORKER_1) currentMetricKey = MetricsKey.CPU_ID_CURRENT_NETTY_WORKER_1;
                        else if (metricKeys[currentIdx] == MetricsKey.CPU_ID_NETTY_WORKER_2) currentMetricKey = MetricsKey.CPU_ID_CURRENT_NETTY_WORKER_2;
                        else if (metricKeys[currentIdx] == MetricsKey.CPU_ID_NETTY_WORKER_3) currentMetricKey = MetricsKey.CPU_ID_CURRENT_NETTY_WORKER_3;
                        else if (metricKeys[currentIdx] == MetricsKey.CPU_ID_NETTY_WORKER_4) currentMetricKey = MetricsKey.CPU_ID_CURRENT_NETTY_WORKER_4;
                        else currentMetricKey = metricKeys[currentIdx];
                        StaticMetricsHolder.recordCpuId(metricKeys[currentIdx], currentMetricKey, cpuId);
                    }
                } catch (Throwable ignored) {}
                r.run();
            };
            return new Thread(bindingTask, prefix + "-" + (currentIdx + 1));
        }
    }
}
