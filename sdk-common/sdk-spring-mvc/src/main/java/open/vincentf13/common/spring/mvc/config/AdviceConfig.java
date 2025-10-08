package open.vincentf13.common.spring.mvc.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import open.vincentf13.common.spring.mvc.advice.ApiResponseBodyAdvice;
import open.vincentf13.common.spring.mvc.exception.RestExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;


@AutoConfiguration
@ConditionalOnClass(WebMvcConfigurer.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class AdviceConfig {


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

}
