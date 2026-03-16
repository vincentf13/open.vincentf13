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
import net.openhft.affinity.AffinityThreadFactory;
import net.openhft.affinity.AffinityStrategies;

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
            bossGroup = new NioEventLoopGroup(1, new DefaultThreadFactory("netty-boss"));
            // 使用 AffinityThreadFactory 為 Netty Worker 分配獨佔核心，消除核心切換開銷
            workerGroup = new NioEventLoopGroup(workerCount, new AffinityThreadFactory("netty-worker", AffinityStrategies.DIFFERENT_CORE));
            try {
                ServerBootstrap b = new ServerBootstrap();
                b.group(bossGroup, workerGroup)
                 .channel(NioServerSocketChannel.class)
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
}
