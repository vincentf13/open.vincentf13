package open.vincentf13.sdk.spring.mvc.correlation;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import open.vincentf13.sdk.core.OpenConstant;
import open.vincentf13.sdk.spring.mvc.web.MvcProperties;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 建立 traceId/requestId，並同步至 request attribute 與 MDC，便於跨服務日誌追蹤。
 */
@RequiredArgsConstructor
public class RequestCorrelationFilter extends OncePerRequestFilter {

    private final MvcProperties.Request properties;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceHeader = OpenConstant.HttpHeader.TRACE_ID.value();
        String requestHeader = OpenConstant.HttpHeader.REQUEST_ID.value();

        String traceId = resolveOrGenerate(request, traceHeader);
        String requestId = resolveOrGenerate(request, requestHeader);

        putIntoContext(traceHeader, traceId);
        putIntoContext(requestHeader, requestId);

        request.setAttribute(traceHeader, traceId);
        request.setAttribute(requestHeader, requestId);

        if (properties.isWriteResponseHeader()) {
            response.setHeader(traceHeader, traceId);
            response.setHeader(requestHeader, requestId);
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(traceHeader);
            MDC.remove(requestHeader);
        }
    }

    private void putIntoContext(String key, String value) {
        if (StringUtils.hasText(key) && StringUtils.hasText(value)) {
            MDC.put(key, value);
        }
    }

    private String resolveOrGenerate(HttpServletRequest request, String headerName) {
        if (!StringUtils.hasText(headerName)) {
            return "unknown";
        }
        Object attribute = request.getAttribute(headerName);
        if (attribute instanceof String attr && StringUtils.hasText(attr)) {
            return attr;
        }
        String headerValue = request.getHeader(headerName);
        if (StringUtils.hasText(headerValue)) {
            return headerValue;
        }
        if (!properties.isGenerateCorrelationIds()) {
            return "unknown";
        }
        return UUID.randomUUID().toString().replace("-", "");
    }
}
