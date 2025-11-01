//package open.vincentf13.sdk.spring.mvc.filter;
//
//import jakarta.servlet.FilterChain;
//import jakarta.servlet.ServletException;
//import jakarta.servlet.http.HttpServletRequest;
//import jakarta.servlet.http.HttpServletResponse;
//import lombok.RequiredArgsConstructor;
//import open.vincentf13.sdk.spring.mvc.config.MvcProperties;
//import org.slf4j.MDC;
//import org.springframework.web.filter.OncePerRequestFilter;
//
//import java.io.IOException;
//import java.util.UUID;
//
///**
// * 建立 traceId/requestId，便於跨服務日誌追蹤。
// */
//@RequiredArgsConstructor
//public class FilterProcessThreadLoacal extends OncePerRequestFilter {
//
//    private final MvcProperties.Request properties;
//
//    @Override
//    protected void doFilterInternal(HttpServletRequest request,
//                                    HttpServletResponse response,
//                                    FilterChain filterChain) throws ServletException, IOException {
//        String traceId = resolveOrGenerate(request, properties.getTraceIdHeader());
//        String requestId = resolveOrGenerate(request, properties.getRequestIdHeader());
//
//        MDC.put("traceId", traceId);
//        MDC.put("requestId", requestId);
//
//        request.setAttribute("traceId", traceId);
//        request.setAttribute("requestId", requestId);
//
//        if (properties.isWriteResponseHeader()) {
//            response.setHeader(properties.getTraceIdHeader(), traceId);
//            response.setHeader(properties.getRequestIdHeader(), requestId);
//        }
//
//        try {
//            filterChain.doFilter(request, response);
//        } finally {
//            MDC.remove("traceId");
//            MDC.remove("requestId");
//        }
//    }
//
//    private String resolveOrGenerate(HttpServletRequest request, String headerName) {
//        String existing = request.getHeader(headerName);
//        if (existing != null && !existing.isBlank()) {
//            return existing;
//        }
//        if (!properties.isGenerateCorrelationIds()) {
//            return "unknown";
//        }
//        // 大幅降低 UUID 撞號概率，同時移除 dash 便於在 header 中傳遞。
//        return UUID.randomUUID().toString().replace("-", "");
//    }
//}
