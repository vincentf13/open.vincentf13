package open.vincentf13.sdk.spring.mvc.log;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;

/**
 Request/response logging filter that wraps the servlet request/response and delegates
 detailed logging to {@link MvcLogService}. Executed once per request so even security
 short-circuits (e.g. 403) will be logged.
 */
public class RequestLoggingFilter extends OncePerRequestFilter {
    
    private final MvcLogService logService;
    
    public RequestLoggingFilter(MvcLogService logService) {
        this.logService = logService;
    }
    
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long start = System.currentTimeMillis();
        ContentCachingRequestWrapper requestWrapper = wrapRequest(request);
        ContentCachingResponseWrapper responseWrapper = wrapResponse(response);
        Exception exception = null;
        try {
            filterChain.doFilter(requestWrapper, responseWrapper);
        } catch (Exception ex) {
            exception = ex;
            throw ex;
        } finally {
            long duration = System.currentTimeMillis() - start;
            try {
                logService.logCompletion(requestWrapper, responseWrapper, null, exception, duration);
            } finally {
                responseWrapper.copyBodyToResponse();
            }
        }
    }
    
    private ContentCachingRequestWrapper wrapRequest(HttpServletRequest request) {
        if (request instanceof ContentCachingRequestWrapper wrapper) {
            return wrapper;
        }
        return new ContentCachingRequestWrapper(request);
    }
    
    private ContentCachingResponseWrapper wrapResponse(HttpServletResponse response) {
        if (response instanceof ContentCachingResponseWrapper wrapper) {
            return wrapper;
        }
        return new ContentCachingResponseWrapper(response);
    }
}
