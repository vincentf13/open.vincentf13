package open.vincentf13.common.spring.mvc.config;

import lombok.Getter;
import lombok.Setter;
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
@Getter
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

    @Getter
    @Setter
    public static class Request {
        /**
         * 是否在請求缺少 header 時自動生成追蹤資訊。
         */
        private boolean generateCorrelationIds = true;
        private String traceIdHeader = "X-Trace-Id";
        private String requestIdHeader = "X-Request-Id";
        private boolean writeResponseHeader = true;
        private int filterOrder = Integer.MIN_VALUE; // highest precedence by default
    }

    @Getter
    @Setter
    public static class Response {
        /**
         * 是否對 REST 回應做統一包裝。
         */
        private boolean wrapEnabled = true;
        /**
         * 不進行包裝的 controller name pattern，支援前綴比對。
         */
        private List<String> ignoreControllerPrefixes = new ArrayList<>();
    }

    @Getter
    @Setter
    public static class Cors {
        private boolean enabled = false;
        private String pathPattern = "/**";
        private List<String> allowedOrigins = new ArrayList<>(List.of("*"));
        private List<String> allowedMethods = new ArrayList<>(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        private List<String> allowedHeaders = new ArrayList<>(List.of("*"));
        private List<String> exposedHeaders = new ArrayList<>();
        private boolean allowCredentials = true;
        private Duration maxAge = Duration.ofHours(1);
        // 將預設 preflight 快取設為 1 小時，可透過屬性調整以符合部署策略。
    }

    @Getter
    @Setter
    public static class I18n {
        private String defaultLocale = Locale.US.toLanguageTag();
        private List<String> supportedLocales = new ArrayList<>(List.of(Locale.US.toLanguageTag()));
        private String localeParamName = "lang";
        private boolean enableLocaleChangeInterceptor = true;
        private String messageBasename = "classpath:i18n/messages";

        public Locale defaultLocale() {
            return Locale.forLanguageTag(defaultLocale);
        }

        public List<Locale> supportedLocales() {
            return supportedLocales.stream()
                    .map(Locale::forLanguageTag)
                    .toList();
        }

    }

    @Getter
    @Setter
    public static class Validation {
        private boolean failFast = true;
    }
}
