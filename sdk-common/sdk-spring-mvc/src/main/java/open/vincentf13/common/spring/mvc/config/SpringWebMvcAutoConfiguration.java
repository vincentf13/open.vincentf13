package open.vincentf13.common.spring.mvc.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import open.vincentf13.common.spring.mvc.advice.ApiResponseBodyAdvice;
import open.vincentf13.common.spring.mvc.exception.RestExceptionHandler;
import open.vincentf13.common.spring.mvc.filter.RequestCorrelationFilter;
import open.vincentf13.common.spring.mvc.interceptor.RequestLoggingInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.format.FormatterRegistry;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.lang.NonNull;
import org.springframework.util.StringUtils;
import org.springframework.validation.Validator;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Spring MVC 自動配置，聚合統一回應、請求追蹤與例外處理等共用設定。
 */
@AutoConfiguration
@ConditionalOnClass(WebMvcConfigurer.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(MvcProperties.class)
public class SpringWebMvcAutoConfiguration {

    /**
     * 建立 RequestLoggingInterceptor，提供統一請求摘要日誌。
     */
    @Bean
    public RequestLoggingInterceptor requestLoggingInterceptor() {
        return new RequestLoggingInterceptor();
    }

    /**
     * 建立 RequestCorrelationFilter，負責補足 traceId/requestId 與同步至 MDC。
     */
    @Bean
    public RequestCorrelationFilter requestCorrelationFilter(MvcProperties properties) {
        return new RequestCorrelationFilter(properties.getRequest());
    }

    /**
     * 將 RequestCorrelationFilter 以指定順序註冊到 Servlet Filter Chain。
     */
    @Bean
    public FilterRegistrationBean<RequestCorrelationFilter> requestCorrelationFilterRegistration(RequestCorrelationFilter filter,
                                                                                                MvcProperties properties) {
        FilterRegistrationBean<RequestCorrelationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(properties.getRequest().getFilterOrder());
        registration.addUrlPatterns("/*");
        return registration;
    }

    /**
     * 提供預設的 RestExceptionHandler，避免每個服務重複實作。
     */
    @Bean
    @ConditionalOnMissingBean
    public RestExceptionHandler restExceptionHandler() {
        return new RestExceptionHandler();
    }

    /**
     * 構建 ApiResponseBodyAdvice，統一包裝回應結果。
     */
    @Bean
    @ConditionalOnMissingBean
    public ApiResponseBodyAdvice apiResponseBodyAdvice(ObjectMapper objectMapper, MvcProperties properties) {
        return new ApiResponseBodyAdvice(objectMapper, properties);
    }

    /**
     * 統一擴充 WebMvcConfigurer：註冊攔截器、格式化器與 MessageConverter。
     */
    @Bean
    public WebMvcConfigurer baseWebMvcConfigurer(ObjectMapper objectMapper,
                                                 RequestLoggingInterceptor requestLoggingInterceptor,
                                                 Validator validator) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(@NonNull InterceptorRegistry registry) { // 註冊全域攔截器
                registry.addInterceptor(requestLoggingInterceptor);
            }

            @Override
            public void addFormatters(@NonNull FormatterRegistry registry) { // 字串 trim 等共用轉換
                registry.addConverter(String.class, String.class, source -> {
                    if (!StringUtils.hasText(source)) {
                        return null;
                    }
                    return source.trim();
                });
            }

            @Override
            public void extendMessageConverters(@NonNull List<HttpMessageConverter<?>> converters) { // 統一 ObjectMapper 與編碼設定
                converters.stream()
                        .filter(MappingJackson2HttpMessageConverter.class::isInstance)
                        .map(MappingJackson2HttpMessageConverter.class::cast)
                        .findFirst()
                        .ifPresent(converter -> {
                            converter.setObjectMapper(objectMapper);
                            converter.setDefaultCharset(StandardCharsets.UTF_8);
                        });

                converters.stream()
                        .filter(StringHttpMessageConverter.class::isInstance)
                        .map(StringHttpMessageConverter.class::cast)
                        .findFirst()
                        .ifPresent(converter -> converter.setDefaultCharset(StandardCharsets.UTF_8));
            }

            @Override
            public Validator getValidator() { // 使用外部註冊的 Validator（支援 fail-fast 等設定）
                return validator;
            }
        };
    }
}
