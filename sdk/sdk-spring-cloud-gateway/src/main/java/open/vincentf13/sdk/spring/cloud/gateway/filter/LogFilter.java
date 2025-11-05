package open.vincentf13.sdk.spring.cloud.gateway.filter;

import open.vincentf13.sdk.spring.cloud.gateway.service.LogService;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class LogFilter implements GlobalFilter, Ordered {

    private final LogService logService;

    public LogFilter(LogService logService) {
        this.logService = logService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        logService.logRequest(exchange);
        long startTime = System.currentTimeMillis();

        return chain.filter(exchange)
                .doOnError(ex -> logService.logForwardFailure(exchange, ex))
                .then(Mono.fromRunnable(() -> {
                    long duration = System.currentTimeMillis() - startTime;
                    logService.logResponse(exchange, duration);
                }));
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
