package open.vincentf13.common.spring.mvc.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;

import java.util.Optional;

import static open.vincentf13.common.core.OpenConstant.BEARER_PREFIX;

/**
 * Web related helper utilities shared across MVC components.
 */
public final class OpenHttpUtils {

    private OpenHttpUtils() {
    }

    public static Optional<String> resolveBearerToken(HttpServletRequest request) {
        if (request == null) {
            return Optional.empty();
        }
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(header) || !header.startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }
        return Optional.of(header.substring(BEARER_PREFIX.length()));
    }
}
