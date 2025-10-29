package open.vincentf13.common.infra.jwt.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import open.vincentf13.common.core.OpenConstant;
import open.vincentf13.common.core.log.OpenLog;
import open.vincentf13.common.infra.jwt.session.JwtSessionService;
import open.vincentf13.common.infra.jwt.token.JwtProperties;
import open.vincentf13.common.infra.jwt.token.OpenJwtToken;
import open.vincentf13.common.infra.jwt.token.model.JwtAuthenticationToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Bearer JWT filter that restores authentication from the Authorization header and (optionally)
 * validates session state through the shared session service.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final OpenJwtToken openJwtToken;
    private final ObjectProvider<JwtSessionService> sessionServiceProvider;
    private final JwtProperties properties;
    private final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    public JwtAuthenticationFilter(OpenJwtToken openJwtToken,
                                   ObjectProvider<JwtSessionService> sessionServiceProvider,
                                   JwtProperties properties) {
        this.openJwtToken = openJwtToken;
        this.sessionServiceProvider = sessionServiceProvider;
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            resolveToken(request)
                .flatMap(openJwtToken::parseAccessToken)
                .filter(this::isAllowed)
                .ifPresent(authentication -> SecurityContextHolder.getContext().setAuthentication(authentication));
        }
        filterChain.doFilter(request, response);
    }

    private boolean isAllowed(JwtAuthenticationToken authentication) {
        if (!properties.isCheckSessionActive()) {
            return true;
        }
        JwtSessionService sessionService = sessionServiceProvider.getIfAvailable();
        if (sessionService == null) {
            return true;
        }
        boolean active = sessionService.isActive(authentication.getSessionId());
        if (!active) {
            OpenLog.info(log,
                    "JwtSessionInactive",
                    "Session inactive, skip authentication",
                    "sessionId", authentication.getSessionId(),
                    "principal", authentication.getName());
        }
        return active;
    }

    private Optional<String> resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(header) || !header.startsWith(OpenConstant.BEARER_PREFIX)) {
            return Optional.empty();
        }
        return Optional.of(header.substring(OpenConstant.BEARER_PREFIX.length()));
    }
}
