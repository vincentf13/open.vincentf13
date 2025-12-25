package open.vincentf13.sdk.spring.cloud.gateway.jwt;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public class AuthorizationForwardFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authorization)) {
            return chain.filter(exchange);
        }
        ServerWebExchange mutatedExchange = exchange.mutate()
                                                   .request(builder -> builder.headers(headers -> {
                                                       headers.remove(HttpHeaders.AUTHORIZATION);
                                                       headers.add(HttpHeaders.AUTHORIZATION, authorization);
                                                   }))
                                                   .build();
        return chain.filter(mutatedExchange);
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 10;
    }
}
