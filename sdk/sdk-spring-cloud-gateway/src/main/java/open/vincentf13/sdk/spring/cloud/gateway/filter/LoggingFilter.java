package open.vincentf13.sdk.spring.cloud.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class LoggingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(LoggingFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        log.info(">>> Request: {} {}", request.getMethod(), request.getURI());
        if (log.isDebugEnabled()) {
            request.getHeaders().forEach((name, values) -> log.debug("Header {}={}", name, values));
        }

        long startTime = System.currentTimeMillis();

        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            ServerHttpResponse response = exchange.getResponse();
            long duration = System.currentTimeMillis() - startTime;

            log.info("<<< Response: {} ({} ms)", response.getStatusCode(), duration);
            if (log.isDebugEnabled()) {
                response.getHeaders().forEach((name, values) -> log.debug("Response header {}={}", name, values));
            }
        }));
    }

    @Override
    public int getOrder() {
        return -1;
    }
}

