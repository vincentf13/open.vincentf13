package open.vincentf13.service.spot_exchange.gateway.config;

import open.vincentf13.service.spot_exchange.gateway.handler.ExchangeWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** 
  WebSocket 通訊配置
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final ExchangeWebSocketHandler handler;

    public WebSocketConfig(ExchangeWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/spot")
                .setAllowedOrigins("*");
    }
}
