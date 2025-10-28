package open.vincentf13.common.sdk.spring.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import open.vincentf13.common.sdk.spring.security.token.OpenJwtToken;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import open.vincentf13.common.core.OpenConstant;

import java.io.IOException;
import java.util.Optional;

/**
 * 基於 Bearer JWT 的一次性過濾器，負責從請求擷取 Token 並還原登入狀態。
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final OpenJwtToken openJwtToken;

    public JwtAuthenticationFilter(OpenJwtToken openJwtToken) {
        this.openJwtToken = openJwtToken;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            // 若尚未有認證資訊，嘗試解析 Authorization 標頭中的 JWT
            resolveToken(request)
                .flatMap(openJwtToken::parse)
                .ifPresent(SecurityContextHolder.getContext()::setAuthentication);
        }
        // 若沒有token，最終會因為沒有 Authentication 物件而回 401
        filterChain.doFilter(request, response);
    }

    private Optional<String> resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(header) || !header.startsWith(OpenConstant.BEARER_PREFIX)) {
            return Optional.empty();
        }
        return Optional.of(header.substring(OpenConstant.BEARER_PREFIX.length()));
    }
}
