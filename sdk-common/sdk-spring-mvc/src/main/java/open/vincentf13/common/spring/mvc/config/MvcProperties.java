package open.vincentf13.common.spring.mvc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Spring MVC 基礎設定，透過 {@code open.vincentf13.mvc.*} 進行調整。
 */
@Validated
@ConfigurationProperties(prefix = "open.vincentf13.mvc")
public class MvcProperties {

    @NestedConfigurationProperty
    private final Request request = new Request();

    @NestedConfigurationProperty
    private final Response response = new Response();

    @NestedConfigurationProperty
    private final Cors cors = new Cors();

    @NestedConfigurationProperty
    private final I18n i18n = new I18n();

    @NestedConfigurationProperty
    private final Validation validation = new Validation();

    public Request getRequest() {
        return request;
    }

    public Response getResponse() {
        return response;
    }

    public Cors getCors() {
        return cors;
    }

    public I18n getI18n() {
        return i18n;
    }

    public Validation getValidation() {
        return validation;
    }

    public static class Request {
        /**
         * 是否在請求缺少 header 時自動生成追蹤資訊。
         */
        private boolean generateCorrelationIds = true;
        private String traceIdHeader = "X-Trace-Id";
        private String requestIdHeader = "X-Request-Id";
        private boolean writeResponseHeader = true;
        private int filterOrder = Integer.MIN_VALUE; // highest precedence by default

        public boolean isGenerateCorrelationIds() {
            return generateCorrelationIds;
        }

        public void setGenerateCorrelationIds(boolean generateCorrelationIds) {
            this.generateCorrelationIds = generateCorrelationIds;
        }

        public String getTraceIdHeader() {
            return traceIdHeader;
        }

        public void setTraceIdHeader(String traceIdHeader) {
            this.traceIdHeader = traceIdHeader;
        }

        public String getRequestIdHeader() {
            return requestIdHeader;
        }

        public void setRequestIdHeader(String requestIdHeader) {
            this.requestIdHeader = requestIdHeader;
        }

        public boolean isWriteResponseHeader() {
            return writeResponseHeader;
        }

        public void setWriteResponseHeader(boolean writeResponseHeader) {
            this.writeResponseHeader = writeResponseHeader;
        }

        public int getFilterOrder() {
            return filterOrder;
        }

        public void setFilterOrder(int filterOrder) {
            this.filterOrder = filterOrder;
        }
    }

    public static class Response {
        /**
         * 是否對 REST 回應做統一包裝。
         */
        private boolean wrapEnabled = true;
        /**
         * 不進行包裝的 controller name pattern，支援前綴比對。
         */
        private List<String> ignoreControllerPrefixes = new ArrayList<>();

        public boolean isWrapEnabled() {
            return wrapEnabled;
        }

        public void setWrapEnabled(boolean wrapEnabled) {
            this.wrapEnabled = wrapEnabled;
        }

        public List<String> getIgnoreControllerPrefixes() {
            return ignoreControllerPrefixes;
        }

        public void setIgnoreControllerPrefixes(List<String> ignoreControllerPrefixes) {
            this.ignoreControllerPrefixes = ignoreControllerPrefixes;
        }
    }

    public static class Cors {
        private boolean enabled = false;
        private String pathPattern = "/**";
        private List<String> allowedOrigins = new ArrayList<>(List.of("*"));
        private List<String> allowedMethods = new ArrayList<>(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        private List<String> allowedHeaders = new ArrayList<>(List.of("*"));
        private List<String> exposedHeaders = new ArrayList<>();
        private boolean allowCredentials = true;
        private Duration maxAge = Duration.ofHours(1);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getPathPattern() {
            return pathPattern;
        }

        public void setPathPattern(String pathPattern) {
            this.pathPattern = pathPattern;
        }

        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }

        public List<String> getAllowedMethods() {
            return allowedMethods;
        }

        public void setAllowedMethods(List<String> allowedMethods) {
            this.allowedMethods = allowedMethods;
        }

        public List<String> getAllowedHeaders() {
            return allowedHeaders;
        }

        public void setAllowedHeaders(List<String> allowedHeaders) {
            this.allowedHeaders = allowedHeaders;
        }

        public List<String> getExposedHeaders() {
            return exposedHeaders;
        }

        public void setExposedHeaders(List<String> exposedHeaders) {
            this.exposedHeaders = exposedHeaders;
        }

        public boolean isAllowCredentials() {
            return allowCredentials;
        }

        public void setAllowCredentials(boolean allowCredentials) {
            this.allowCredentials = allowCredentials;
        }

        public Duration getMaxAge() {
            return maxAge;
        }

        public void setMaxAge(Duration maxAge) {
            this.maxAge = maxAge;
        }
    }

    public static class I18n {
        private String defaultLocale = Locale.US.toLanguageTag();
        private List<String> supportedLocales = new ArrayList<>(List.of(Locale.US.toLanguageTag()));
        private String localeParamName = "lang";
        private boolean enableLocaleChangeInterceptor = true;
        private String messageBasename = "classpath:i18n/messages";

        public Locale defaultLocale() {
            return Locale.forLanguageTag(defaultLocale);
        }

        public void setDefaultLocale(String defaultLocale) {
            this.defaultLocale = defaultLocale;
        }

        public List<Locale> supportedLocales() {
            return supportedLocales.stream()
                    .map(Locale::forLanguageTag)
                    .toList();
        }

        public List<String> getSupportedLocales() {
            return supportedLocales;
        }

        public void setSupportedLocales(List<String> supportedLocales) {
            this.supportedLocales = supportedLocales;
        }

        public String getLocaleParamName() {
            return localeParamName;
        }

        public void setLocaleParamName(String localeParamName) {
            this.localeParamName = localeParamName;
        }

        public boolean isEnableLocaleChangeInterceptor() {
            return enableLocaleChangeInterceptor;
        }

        public void setEnableLocaleChangeInterceptor(boolean enableLocaleChangeInterceptor) {
            this.enableLocaleChangeInterceptor = enableLocaleChangeInterceptor;
        }

        public String getMessageBasename() {
            return messageBasename;
        }

        public void setMessageBasename(String messageBasename) {
            this.messageBasename = messageBasename;
        }
    }

    public static class Validation {
        private boolean failFast = true;

        public boolean isFailFast() {
            return failFast;
        }

        public void setFailFast(boolean failFast) {
            this.failFast = failFast;
        }
    }
}
