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

import io.netty.util.concurrent.DefaultThreadFactory;
import open.vincentf13.service.spot.infra.Constants.MetricsKey;
import open.vincentf13.service.spot.infra.Constants.Ws;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.infra.util.AffinityUtil;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import static open.vincentf13.service.spot.infra.Constants.Ws;

@Slf4j
@Component
public class NettyServer {
    @Value("${websocket.port:8080}")
    private int port;

    @Value("${netty.worker.count:2}")
    private int workerCount;

    private final WsCommandInboundHandler wsHandler;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    public NettyServer(WsCommandInboundHandler wsHandler) {
        this.wsHandler = wsHandler;
    }

    @PostConstruct
    public void start() {
        new Thread(() -> {
            bossGroup = new NioEventLoopGroup(1, new AffinityThreadFactory("netty-boss", MetricsKey.CPU_ID_NETTY_BOSS));
            workerGroup = new NioEventLoopGroup(workerCount, new AffinityThreadFactory("netty-worker", 
                MetricsKey.CPU_ID_NETTY_WORKER_1, MetricsKey.CPU_ID_NETTY_WORKER_2, 
                MetricsKey.CPU_ID_NETTY_WORKER_3, MetricsKey.CPU_ID_NETTY_WORKER_4));
            try {
                ServerBootstrap b = new ServerBootstrap();
                b.group(bossGroup, workerGroup)
                 .channel(NioServerSocketChannel.class)
                 // --- 性能優化：Socket 選項 ---
                 .option(io.netty.channel.ChannelOption.SO_BACKLOG, 8192) // 處理爆發連線
                 .option(io.netty.channel.ChannelOption.SO_REUSEADDR, true)
                 .childOption(io.netty.channel.ChannelOption.TCP_NODELAY, true) // 關閉 Nagle，低延遲關鍵
                 .childOption(io.netty.channel.ChannelOption.SO_KEEPALIVE, true)
                 .childOption(io.netty.channel.ChannelOption.ALLOCATOR, io.netty.buffer.PooledByteBufAllocator.DEFAULT) // 強制使用內存池
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

                log.info("Netty WebSocket Server started on port {} with {} workers", port, workerCount);
                b.bind(port).sync().channel().closeFuture().sync();
            } catch (Exception e) {
                log.error("Netty Server Error", e);
            } finally {
                stop();
            }
        }, "netty-server-starter").start();
    }

    @PreDestroy
    public void stop() {
        if (bossGroup != null) bossGroup.shutdownGracefully();
        if (workerGroup != null) workerGroup.shutdownGracefully();
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
            // 延遲綁定策略：在 Runnable 執行時才進行綁定，確保執行緒已完全啟動
            Runnable bindingTask = () -> {
                int cpuId = AffinityUtil.acquireAndBind();
                if (cpuId != -1 && metricKeys != null && currentIdx < metricKeys.length) {
                    Storage.self().metricsHistory().put(metricKeys[currentIdx], (long) cpuId);
                }
                r.run();
            };
            return new Thread(bindingTask, prefix + "-" + (currentIdx + 1));
        }
    }
}
