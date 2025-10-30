package open.vincentf13.common.sdk.spring.cloud.gateway.security;

import open.vincentf13.common.core.OpenConstant;
import open.vincentf13.common.core.log.OpenLog;
import open.vincentf13.common.infra.jwt.session.JwtSessionService;
import open.vincentf13.common.infra.jwt.token.JwtProperties;
import open.vincentf13.common.infra.jwt.token.OpenJwt;
import open.vincentf13.common.infra.jwt.token.model.JwtAuthenticationToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

class JwtGatewayFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtGatewayFilter.class);

    private final OpenJwt openJwt;
    private final JwtProperties jwtProperties;
    private final ObjectProvider<JwtSessionService> sessionServiceProvider;
    private final GatewayJwtProperties gatewayJwtProperties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    JwtGatewayFilter(OpenJwt openJwt,
                     JwtProperties jwtProperties,
                     ObjectProvider<JwtSessionService> sessionServiceProvider,
                     GatewayJwtProperties gatewayJwtProperties) {
        this.openJwt = openJwt;
        this.jwtProperties = jwtProperties;
        this.sessionServiceProvider = sessionServiceProvider;
        this.gatewayJwtProperties = gatewayJwtProperties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!gatewayJwtProperties.isEnabled() || shouldBypass(exchange.getRequest().getPath().value())) {
            return chain.filter(exchange);
        }

        Optional<String> tokenValue = resolveToken(exchange);
        if (tokenValue.isEmpty()) {
            return unauthorized(exchange, "Missing Bearer token");
        }

        Optional<JwtAuthenticationToken> authentication = openJwt.parseAccessToken(tokenValue.get());
        if (authentication.isEmpty()) {
            OpenLog.warn(log, "GatewayJwtInvalid", "JWT access token validation failed", "token", "redacted");
            return unauthorized(exchange, "Invalid access token");
        }

        JwtAuthenticationToken auth = authentication.get();


        if (!isSessionActive(auth)) {
            return unauthorized(exchange, "Session inactive");
        }

        return chain.filter(exchange)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    private boolean shouldBypass(String path) {
        List<String> permitPaths = gatewayJwtProperties.getPermitPaths();
        if (permitPaths == null || permitPaths.isEmpty()) {
            return false;
        }
        return permitPaths.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private boolean isSessionActive(JwtAuthenticationToken authentication) {
        if (!jwtProperties.isCheckSessionActive()) {
            return true;
        }
        JwtSessionService sessionService = sessionServiceProvider.getIfAvailable();
        if (sessionService == null) {
            return true;
        }
        boolean active = sessionService.isActive(authentication.getSessionId());
        if (!active) {
            OpenLog.info(log,
                    "GatewayJwtSessionInactive",
                    "Session inactive, rejecting request",
                    "sessionId", authentication.getSessionId(),
                    "principal", authentication.getName());
        }
        return active;
    }

    private Optional<String> resolveToken(ServerWebExchange exchange) {
        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(header) || !header.startsWith(OpenConstant.BEARER_PREFIX)) {
            return Optional.empty();
        }
        return Optional.of(header.substring(OpenConstant.BEARER_PREFIX.length()).trim());
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String reason) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.empty();
        }
        OpenLog.info(log, "GatewayJwtUnauthorized", "Request rejected", "reason", reason);
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }
}
