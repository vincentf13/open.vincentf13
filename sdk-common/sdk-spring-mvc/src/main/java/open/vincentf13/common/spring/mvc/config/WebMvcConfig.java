package open.vincentf13.common.spring.mvc.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import open.vincentf13.common.spring.mvc.interceptor.RequestLoggingInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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


@AutoConfiguration
@ConditionalOnClass(WebMvcConfigurer.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(MvcProperties.class)
public class WebMvcConfig {


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
