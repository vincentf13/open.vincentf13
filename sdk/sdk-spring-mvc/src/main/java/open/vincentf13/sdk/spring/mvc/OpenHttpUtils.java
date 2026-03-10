package open.vincentf13.sdk.spring.mvc;

import jakarta.servlet.http.HttpServletRequest;
import open.vincentf13.sdk.core.OpenConstant;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 Web related helper utilities shared across MVC components.
 */
public final class OpenHttpUtils {
    
    private OpenHttpUtils() {
    }
    
    public static Optional<String> resolveBearerToken(HttpServletRequest request) {
        if (request == null) {
            return Optional.empty();
        }
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        String prefix = OpenConstant.HttpHeader.Authorization.BEARER_PREFIX.value();
        if (!StringUtils.hasText(header) || !header.startsWith(prefix)) {
            return Optional.empty();
        }
        return Optional.of(header.substring(prefix.length()));
    }
}
