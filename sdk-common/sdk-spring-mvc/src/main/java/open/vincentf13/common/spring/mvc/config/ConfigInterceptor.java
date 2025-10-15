package open.vincentf13.common.spring.mvc.config;

import open.vincentf13.common.spring.mvc.interceptor.InterceptorRequestLogging;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;


@AutoConfiguration
@ConditionalOnClass(WebMvcConfigurer.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ConfigInterceptor {


    /**
     * 建立 RequestLoggingInterceptor，提供統一請求摘要日誌。
     */
    @Bean
    public InterceptorRequestLogging requestLoggingInterceptor() {
        return new InterceptorRequestLogging();
    }

}
