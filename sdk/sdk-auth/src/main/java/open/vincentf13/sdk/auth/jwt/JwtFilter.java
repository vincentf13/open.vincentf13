package open.vincentf13.sdk.auth.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import open.vincentf13.sdk.spring.mvc.OpenHttpUtils;
import open.vincentf13.sdk.core.log.OpenLog;
import open.vincentf13.sdk.auth.jwt.JwtAuthEventEnum;
import open.vincentf13.sdk.auth.jwt.session.JwtSessionService;
import open.vincentf13.sdk.auth.jwt.config.JwtProperties;
import open.vincentf13.sdk.auth.jwt.model.JwtParseInfo;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Bearer JWT filter that restores authentication from the Authorization header and (optionally)
 * validates session state through the shared session service.
 */
public class JwtFilter extends OncePerRequestFilter {

    private final OpenJwtService openJwtService;
    private final ObjectProvider<JwtSessionService> sessionServiceProvider;
    private final JwtProperties properties;
    public JwtFilter(OpenJwtService openJwtService,
                     ObjectProvider<JwtSessionService> sessionServiceProvider,
                     JwtProperties properties) {
        this.openJwtService = openJwtService;
        this.sessionServiceProvider = sessionServiceProvider;
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            OpenHttpUtils.resolveBearerToken(request)
                .flatMap(openJwtService::parseAccessToken)
                .filter(this::isAllowed)
                .ifPresent(authentication -> SecurityContextHolder.getContext().setAuthentication(authentication));
        }
        filterChain.doFilter(request, response);
    }

    private boolean isAllowed(JwtParseInfo authentication) {
        if (!properties.isCheckSessionActive()) {
            return true;
        }
        JwtSessionService sessionService = sessionServiceProvider.getIfAvailable();
        if (sessionService == null) {
            return true;
        }
        boolean active = sessionService.isActive(authentication.getSessionId());
        if (!active) {
            OpenLog.info(JwtAuthEventEnum.JWT_SESSION_INACTIVE,
                    "sessionId", authentication.getSessionId(),
                    "principal", authentication.getName());
        }
        return active;
    }

}
