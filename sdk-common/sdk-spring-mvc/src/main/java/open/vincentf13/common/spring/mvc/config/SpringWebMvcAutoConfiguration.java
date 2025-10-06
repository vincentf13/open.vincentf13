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
     * 註冊 MVC 請求日誌攔截器，統一輸出請求摘要與耗時。
     */
    @Bean
    public RequestLoggingInterceptor requestLoggingInterceptor() {
        return new RequestLoggingInterceptor();
    }

    /**
     * 產生 traceId/requestId，並寫入 Header 與 MDC，支援跨服務追蹤。
     */
    @Bean
    public RequestCorrelationFilter requestCorrelationFilter(MvcProperties properties) {
        return new RequestCorrelationFilter(properties.getRequest());
    }

    /**
     * 控制 RequestCorrelationFilter 的註冊順序與生效範圍。
     */
    @Bean
    public FilterRegistrationBean<RequestCorrelationFilter> requestCorrelationFilterRegistration(RequestCorrelationFilter filter,
                                                                                                MvcProperties properties) {
        FilterRegistrationBean<RequestCorrelationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(properties.getRequest().getFilterOrder());
        registration.addUrlPatterns("/*");
        return registration;
    }

    @Bean
    @ConditionalOnMissingBean
    public RestExceptionHandler restExceptionHandler() {
        return new RestExceptionHandler();
    }

    @Bean
    @ConditionalOnMissingBean
    public ApiResponseBodyAdvice apiResponseBodyAdvice(ObjectMapper objectMapper, MvcProperties properties) {
        return new ApiResponseBodyAdvice(objectMapper, properties);
    }

    @Bean
    public WebMvcConfigurer baseWebMvcConfigurer(ObjectMapper objectMapper,
                                                 RequestLoggingInterceptor requestLoggingInterceptor,
                                                 Validator validator) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(@NonNull InterceptorRegistry registry) {
                registry.addInterceptor(requestLoggingInterceptor);
            }

            @Override
            public void addFormatters(@NonNull FormatterRegistry registry) {
                registry.addConverter(String.class, String.class, source -> {
                    if (!StringUtils.hasText(source)) {
                        return null;
                    }
                    return source.trim();
                });
            }

            @Override
            public void extendMessageConverters(@NonNull List<HttpMessageConverter<?>> converters) {
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
            public Validator getValidator() {
                return validator;
            }
        };
    }
}
