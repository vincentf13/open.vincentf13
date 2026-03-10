package open.vincentf13.service.spot.gateway.ws;

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

import static open.vincentf13.service.spot.infra.Constants.Ws;

@Slf4j
@Component
public class NettyServer {
    @Value("${server.port:8081}")
    private int port;

    private final WsHandler wsHandler;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    public NettyServer(WsHandler wsHandler) {
        this.wsHandler = wsHandler;
    }

    @PostConstruct
    public void start() {
        new Thread(() -> {
            bossGroup = new NioEventLoopGroup(1);
            workerGroup = new NioEventLoopGroup();
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

                log.info("Netty WebSocket Server started on port {}", port);
                b.bind(port).sync().channel().closeFuture().sync();
            } catch (Exception e) {
                log.error("Netty Server Error", e);
            } finally {
                stop();
            }
        }, "netty-server").start();
    }

    @PreDestroy
    public void stop() {
        if (bossGroup != null) bossGroup.shutdownGracefully();
        if (workerGroup != null) workerGroup.shutdownGracefully();
    }
}
