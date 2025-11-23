package open.vincentf13.sdk.spring.cloud.openfeign.interceptor;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import open.vincentf13.sdk.core.OpenConstant;
import open.vincentf13.sdk.spring.cloud.openfeign.interceptor.jwt.FeignAuthorizationProvider;
import open.vincentf13.sdk.spring.cloud.openfeign.interceptor.apikey.FeignApiKeyProvider;
import org.slf4j.MDC;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Locale;
import java.util.Optional;

public class DefaultFeignRequestInterceptor implements RequestInterceptor {

    private final FeignAuthorizationProvider authorizationProvider;
    private final FeignApiKeyProvider apiKeyProvider;

    public DefaultFeignRequestInterceptor(FeignAuthorizationProvider authorizationProvider,
                                          FeignApiKeyProvider apiKeyProvider) {
        this.authorizationProvider = authorizationProvider;
        this.apiKeyProvider = apiKeyProvider;
    }

    @Override
    public void apply(RequestTemplate template) {
        attachAuthorization(template);
        attachApiKey(template);
        attachTraceHeaders(template);
        attachLocale(template);
    }

    private void attachApiKey(RequestTemplate template) {
        if (template.headers().containsKey(OpenConstant.Header.API_KEY.value())) {
            return;
        }
        apiKeyProvider.apiKey()
                .filter(StringUtils::hasText)
                .ifPresent(value -> template.header(OpenConstant.Header.API_KEY.value(), value));
    }

    private void attachAuthorization(RequestTemplate template) {
        Optional<String> header = authorizationProvider.authorizationHeader();
        if (template.headers().containsKey(HttpHeaders.AUTHORIZATION)) {
            return;
        }
        header.filter(StringUtils::hasText)
                .ifPresent(value -> template.header(HttpHeaders.AUTHORIZATION, value));
    }

    private void attachTraceHeaders(RequestTemplate template) {
        String traceId = resolveAttribute(OpenConstant.Header.TRACE_ID.value());
        if (!template.headers().containsKey(OpenConstant.Header.TRACE_ID.value()) && StringUtils.hasText(traceId)) {
            template.header(OpenConstant.Header.TRACE_ID.value(), traceId);
        }
        String requestId = resolveAttribute(OpenConstant.Header.REQUEST_ID.value());
        if (!template.headers().containsKey(OpenConstant.Header.REQUEST_ID.value()) && StringUtils.hasText(requestId)) {
            template.header(OpenConstant.Header.REQUEST_ID.value(), requestId);
        }
    }

    private void attachLocale(RequestTemplate template) {
        if (template.headers().containsKey(HttpHeaders.ACCEPT_LANGUAGE)) {
            return;
        }
        Locale locale = LocaleContextHolder.getLocale();
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            Locale requestLocale = servletAttributes.getRequest().getLocale();
            if (requestLocale != null) {
                locale = requestLocale;
            }
        }
        template.header(HttpHeaders.ACCEPT_LANGUAGE, locale.toLanguageTag());
    }

    private String resolveAttribute(String headerName) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            var request = servletAttributes.getRequest();
            Object value = request.getAttribute(headerName);
            if (value instanceof String attrValue && StringUtils.hasText(attrValue)) {
                return attrValue;
            }
            String headerValue = request.getHeader(headerName);
            if (StringUtils.hasText(headerValue)) {
                return headerValue;
            }
        }
        String mdcValue = MDC.get(headerName);
        return StringUtils.hasText(mdcValue) ? mdcValue : null;
    }
}
