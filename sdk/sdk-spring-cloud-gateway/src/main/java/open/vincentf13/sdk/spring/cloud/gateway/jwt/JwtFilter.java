package open.vincentf13.sdk.spring.cloud.gateway.jwt;

import open.vincentf13.sdk.auth.jwt.OpenJwtService;
import open.vincentf13.sdk.auth.jwt.model.JwtParseInfo;
import open.vincentf13.sdk.auth.jwt.session.JwtSessionService;
import open.vincentf13.sdk.core.OpenConstant;
import open.vincentf13.sdk.core.log.OpenLog;
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

public class JwtFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtFilter.class);

    private final OpenJwtService openJwtService;
    private final JwtProperties jwtProperties;
    private final ObjectProvider<JwtSessionService> sessionServiceProvider;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public JwtFilter(OpenJwtService openJwtService,
              ObjectProvider<JwtSessionService> sessionServiceProvider,
              JwtProperties gatewayJwtProperties) {
        this.openJwtService = openJwtService;
        this.sessionServiceProvider = sessionServiceProvider;
        this.jwtProperties = gatewayJwtProperties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!jwtProperties.isEnabled() || shouldBypass(exchange.getRequest().getPath().value())) {
            return chain.filter(exchange);
        }

        // 有帶 jwtToken 就依照配置效驗是否在線上，沒帶token就放行，由資源服務器的 @publicKey , @privateKey, jwt效驗 鑑定權限
        Optional<String> tokenValue = resolveToken(exchange);
        if (tokenValue.isEmpty()) {
            return chain.filter(exchange); // 無 jwtToken 放行，由後端資源服務自行判定授權
        }

        Optional<JwtParseInfo> authentication = openJwtService.parseAccessToken(tokenValue.get());
        if (authentication.isEmpty()) {
            OpenLog.warn(log, "GatewayJwtInvalid", "JWT access jwtToken validation failed", "jwtToken", "redacted");
            return unauthorized(exchange, "Invalid access jwtToken");
        }

        JwtParseInfo auth = authentication.get();

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
        List<String> permitPaths = jwtProperties.getPermitPaths();
        if (permitPaths == null || permitPaths.isEmpty()) {
            return false;
        }
        return permitPaths.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private boolean isSessionActive(JwtParseInfo authentication) {
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
        if (!StringUtils.hasText(header) || !header.startsWith(OpenConstant.HttpHeader.Authorization.BEARER_PREFIX.value())) {
            return Optional.empty();
        }
        return Optional.of(header.substring(OpenConstant.HttpHeader.Authorization.BEARER_PREFIX.value().length()).trim());
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
