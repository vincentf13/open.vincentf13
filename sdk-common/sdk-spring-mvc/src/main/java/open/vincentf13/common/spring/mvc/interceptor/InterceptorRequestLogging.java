package open.vincentf13.common.spring.mvc.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.common.core.log.OpenLog;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 簡易 MVC 輸入/輸出日誌。用於記錄 URI 與耗時，便於排查慢請求。
 */
@Slf4j
public class InterceptorRequestLogging implements HandlerInterceptor {

    private static final String START_TIME = InterceptorRequestLogging.class.getName() + ".START_TIME";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START_TIME, System.currentTimeMillis());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        Object startAttr = request.getAttribute(START_TIME);
        long duration = startAttr instanceof Long start ? System.currentTimeMillis() - start : -1L;
        // 維持固定格式讓日誌聚合工具好做查詢。
        if (ex != null) {
            OpenLog.error(log, "MvcRequestFailed", "MVC request failed",
                    ex,
                    "method", request.getMethod(),
                    "uri", request.getRequestURI(),
                    "status", response.getStatus(),
                    "durationMs", duration);
            return;
        }
        OpenLog.info(log, "MvcRequestCompleted", "MVC request completed",
                "method", request.getMethod(),
                "uri", request.getRequestURI(),
                "status", response.getStatus(),
                "durationMs", duration);
    }
}
