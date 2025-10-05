package open.vincentf13.common.spring.mvc.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 簡易 MVC 輸入/輸出日誌。用於記錄 URI 與耗時，便於排查慢請求。
 */
public class RequestLoggingInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingInterceptor.class);
    private static final String START_TIME = RequestLoggingInterceptor.class.getName() + ".START_TIME";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START_TIME, System.currentTimeMillis());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        Object startAttr = request.getAttribute(START_TIME);
        long duration = startAttr instanceof Long start ? System.currentTimeMillis() - start : -1L;
        if (ex != null) {
            log.error("[MVC] {} {} failed (status={}, duration={}ms)", request.getMethod(), request.getRequestURI(), response.getStatus(), duration, ex);
            return;
        }
        log.info("[MVC] {} {} completed (status={}, duration={}ms)", request.getMethod(), request.getRequestURI(), response.getStatus(), duration);
    }
}
