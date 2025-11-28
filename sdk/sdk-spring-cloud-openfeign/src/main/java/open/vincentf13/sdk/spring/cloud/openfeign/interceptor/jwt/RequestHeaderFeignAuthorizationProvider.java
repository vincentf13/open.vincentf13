package open.vincentf13.sdk.spring.cloud.openfeign.interceptor.jwt;

import jakarta.servlet.http.HttpServletRequest;
import open.vincentf13.sdk.core.OpenConstant;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

/*
  Propagates the current HTTP request's bearer token to downstream Feign calls.
 */
public class RequestHeaderFeignAuthorizationProvider implements FeignAuthorizationProvider {

    @Override
    public Optional<String> authorizationHeader() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            HttpServletRequest request = servletAttributes.getRequest();
            if (request != null) {
                String header = request.getHeader(HttpHeaders.AUTHORIZATION);
                if (StringUtils.hasText(header) && header.startsWith(OpenConstant.HttpHeader.Authorization.BEARER_PREFIX.value())) {
                    return Optional.of(header);
                }
            }
        }
        return Optional.empty();
    }
}
