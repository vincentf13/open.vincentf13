package open.vincentf13.sdk.spring.mvc.log;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import open.vincentf13.sdk.core.log.OpenLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.springframework.web.util.WebUtils;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class MvcLogService {

    private static final Logger log = LoggerFactory.getLogger(MvcLogService.class);
    private static final int MAX_BODY_PREVIEW_LENGTH = 4096;
    private static final Set<String> TEXT_SUB_TYPES = Set.of(
            "json", "xml", "javascript", "x-www-form-urlencoded", "html", "plain", "css", "csv"
    );

    public void logCompletion(HttpServletRequest request,
                              HttpServletResponse response,
                              Object handler,
                              Exception ex,
                              long durationMs) {
        ContentCachingRequestWrapper requestWrapper = WebUtils.getNativeRequest(request, ContentCachingRequestWrapper.class);
        ContentCachingResponseWrapper responseWrapper = WebUtils.getNativeResponse(response, ContentCachingResponseWrapper.class);

        BodySnippet requestBody = extractRequestBody(requestWrapper, request);
        BodySnippet responseBody = extractResponseBody(responseWrapper, response);

        Map<String, String> requestHeaders = extractRequestHeaders(request);
        Map<String, String> responseHeaders = extractResponseHeaders(response);

        String method = request.getMethod();
        String uri = buildRequestUri(request);
        Object handlerRef = resolveHandler(handler, request);
        String handlerDisplay = formatHandler(handlerRef);
        String handlerRaw = handlerRef != null ? handlerRef.toString() : "<unknown>";
        String matchingPattern = resolvePattern(request);
        String clientIp = request.getRemoteAddr();
        int status = response.getStatus();

        if (ex != null) {
            OpenLog.error(log, "MvcRequestFailed", "MVC request failed", ex,
                    "method", method,
                    "uri", uri,
                    "status", status,
                    "durationMs", durationMs,
                    "handler", handlerDisplay,
                    "pattern", matchingPattern,
                    "clientIp", clientIp,
                    "requestBytes", requestBody.length(),
                    "responseBytes", responseBody.length());
        } else {
            OpenLog.info(log, "MvcRequestCompleted", "MVC request completed",
                    "method", method,
                    "uri", uri,
                    "status", status,
                    "durationMs", durationMs,
                    "handler", handlerDisplay,
                    "pattern", matchingPattern,
                    "clientIp", clientIp,
                    "requestBytes", requestBody.length(),
                    "responseBytes", responseBody.length());
        }

        OpenLog.debug(log, "MvcRequestDetail", () -> buildVerboseLog(method, uri, clientIp, handlerDisplay,
                        matchingPattern, handlerRaw, status, durationMs, requestHeaders, requestBody.preview(),
                        responseHeaders, responseBody.preview()),
                "status", status,
                "durationMs", durationMs,
                "requestBytes", requestBody.length(),
                "responseBytes", responseBody.length());
    }

    private String buildRequestUri(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String query = request.getQueryString();
        return query != null ? uri + '?' + query : uri;
    }

    private Map<String, String> extractRequestHeaders(HttpServletRequest request) {
        List<String> names = Collections.list(request.getHeaderNames());
        Map<String, String> headers = new LinkedHashMap<>();
        for (String name : names) {
            List<String> values = Collections.list(request.getHeaders(name));
            headers.put(name, String.join(",", values));
        }
        return headers;
    }

    private Map<String, String> extractResponseHeaders(HttpServletResponse response) {
        Map<String, String> headers = new LinkedHashMap<>();
        for (String name : response.getHeaderNames()) {
            List<String> values = new ArrayList<>(response.getHeaders(name));
            headers.put(name, String.join(",", values));
        }
        return headers;
    }

    private BodySnippet extractRequestBody(ContentCachingRequestWrapper wrapper, HttpServletRequest request) {
        if (wrapper == null) {
            return BodySnippet.unavailable();
        }
        byte[] content = wrapper.getContentAsByteArray();
        String contentType = request.getContentType();
        String encoding = wrapper.getCharacterEncoding();
        return toBodySnippet(content, contentType, encoding);
    }

    private BodySnippet extractResponseBody(ContentCachingResponseWrapper wrapper, HttpServletResponse response) {
        if (wrapper == null) {
            return BodySnippet.unavailable();
        }
        byte[] content = wrapper.getContentAsByteArray();
        String contentType = wrapper.getContentType();
        if (!StringUtils.hasText(contentType)) {
            contentType = response.getContentType();
        }
        String encoding = wrapper.getCharacterEncoding();
        if (!StringUtils.hasText(encoding)) {
            encoding = response.getCharacterEncoding();
        }
        return toBodySnippet(content, contentType, encoding);
    }

    private BodySnippet toBodySnippet(byte[] content, String contentType, String encoding) {
        if (content == null || content.length == 0) {
            return new BodySnippet("", 0);
        }
        if (!isTextContent(contentType)) {
            String preview = String.format("<binary length=%d type=%s>", content.length, contentType);
            return new BodySnippet(preview, content.length);
        }
        Charset charset = resolveCharset(encoding);
        String text = new String(content, charset);
        String preview = abbreviate(text, MAX_BODY_PREVIEW_LENGTH);
        return new BodySnippet(preview, content.length);
    }

    private Charset resolveCharset(String encoding) {
        if (!StringUtils.hasText(encoding)) {
            return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(encoding);
        } catch (Exception ex) {
            return StandardCharsets.UTF_8;
        }
    }

    private boolean isTextContent(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return true;
        }
        String type = contentType.toLowerCase(Locale.ROOT);
        if (type.startsWith("text/")) {
            return true;
        }
        for (String candidate : TEXT_SUB_TYPES) {
            if (type.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private String abbreviate(String text, int maxChars) {
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars) + "...(truncated)";
    }

    private String buildVerboseLog(String method,
                                   String uri,
                                   String clientIp,
                                   String handlerDisplay,
                                   String matchingPattern,
                                   String handlerRaw,
                                   int status,
                                   long durationMs,
                                   Map<String, String> requestHeaders,
                                   String requestBody,
                                   Map<String, String> responseHeaders,
                                   String responseBody) {
        String newline = System.lineSeparator();
        StringBuilder sb = new StringBuilder();
        sb.append("=== HTTP Request ===").append(newline);
        sb.append(method).append(' ').append(uri).append(newline);
        sb.append("Client IP: ").append(clientIp).append(newline);
        sb.append("Handler: ").append(handlerDisplay).append(newline);
        sb.append("Pattern: ").append(matchingPattern).append(newline);
        sb.append("Headers:").append(formatHeadersForDebug(requestHeaders)).append(newline);
        sb.append("Body: ").append(requestBody.isEmpty() ? "<empty>" : requestBody).append(newline);
        sb.append("=== HTTP Response ===").append(newline);
        sb.append("Status: ").append(status).append(newline);
        sb.append("Duration: ").append(durationMs).append(" ms").append(newline);
        sb.append("Headers:").append(formatHeadersForDebug(responseHeaders)).append(newline);
        sb.append("Body: ").append(responseBody.isEmpty() ? "<empty>" : responseBody).append(newline);
        sb.append("Raw Handler: ").append(handlerRaw);
        return sb.toString();
    }

    private String formatHeadersForDebug(Map<String, String> headers) {
        if (headers.isEmpty()) {
            return " (none)";
        }
        String newline = System.lineSeparator();
        return headers.entrySet().stream()
                .map(entry -> entry.getKey() + ": " + entry.getValue())
                .collect(Collectors.joining(newline + "  ", "" + newline + "  ", ""));
    }

    private Object resolveHandler(Object handler, HttpServletRequest request) {
        if (handler != null) {
            return handler;
        }
        return request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
    }

    private String resolvePattern(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return pattern != null ? pattern.toString() : "<unknown>";
    }

    private String formatHandler(Object handler) {
        if (handler instanceof HandlerMethod handlerMethod) {
            return handlerMethod.getBeanType().getSimpleName() + '#' + handlerMethod.getMethod().getName();
        }
        return handler != null ? handler.toString() : "<unknown>";
    }

    private record BodySnippet(String preview, int length) {
        private static BodySnippet unavailable() {
            return new BodySnippet("<unavailable>", 0);
        }
    }
}
