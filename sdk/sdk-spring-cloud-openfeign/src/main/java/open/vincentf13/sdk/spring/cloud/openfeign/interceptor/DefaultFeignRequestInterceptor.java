package open.vincentf13.sdk.spring.cloud.openfeign.interceptor;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import open.vincentf13.sdk.core.OpenConstant;
import open.vincentf13.sdk.spring.cloud.openfeign.auth.FeignAuthorizationProvider;
import open.vincentf13.sdk.spring.cloud.openfeign.apikey.FeignApiKeyProvider;
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
        if (template.headers().containsKey(OpenConstant.API_KEY_HEADER)) {
            return;
        }
        apiKeyProvider.apiKey()
                .filter(StringUtils::hasText)
                .ifPresent(value -> template.header(OpenConstant.API_KEY_HEADER, value));
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
        String traceId = resolveAttribute(OpenConstant.TRACE_ID_HEADER, OpenConstant.TRACE_ID_KEY);
        if (!template.headers().containsKey(OpenConstant.TRACE_ID_HEADER) && StringUtils.hasText(traceId)) {
            template.header(OpenConstant.TRACE_ID_HEADER, traceId);
        }
        String requestId = resolveAttribute(OpenConstant.REQUEST_ID_HEADER, OpenConstant.REQUEST_ID_KEY);
        if (!template.headers().containsKey(OpenConstant.REQUEST_ID_HEADER) && StringUtils.hasText(requestId)) {
            template.header(OpenConstant.REQUEST_ID_HEADER, requestId);
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

    private String resolveAttribute(String headerName, String mdcKey) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            Object value = servletAttributes.getRequest().getAttribute(mdcKey);
            if (value instanceof String stringValue && StringUtils.hasText(stringValue)) {
                return stringValue;
            }
            value = servletAttributes.getRequest().getAttribute(headerName);
            if (value instanceof String stringValue && StringUtils.hasText(stringValue)) {
                return stringValue;
            }
        }
        String mdcValue = MDC.get(mdcKey);
        return StringUtils.hasText(mdcValue) ? mdcValue : null;
    }
}
