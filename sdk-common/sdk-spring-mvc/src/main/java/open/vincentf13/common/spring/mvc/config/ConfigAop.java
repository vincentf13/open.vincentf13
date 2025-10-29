package open.vincentf13.common.spring.mvc.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import open.vincentf13.common.spring.mvc.advice.AopApiResponseBody;
import open.vincentf13.common.spring.mvc.advice.AopRestException;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;


@AutoConfiguration
@ConditionalOnClass(WebMvcConfigurer.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ConfigAop {


    /**
     * 提供預設的 RestExceptionHandler，避免每個服務重複實作。
     */
    @Bean
    @ConditionalOnMissingBean
    public AopRestException restExceptionHandler() {
        return new AopRestException();
    }

    /**
     * 構建 ApiResponseBodyAdvice，統一包裝回應結果。
     */
    @Bean
    @ConditionalOnMissingBean
    public AopApiResponseBody apiResponseBodyAdvice(ObjectMapper objectMapper, MvcProperties properties) {
        return new AopApiResponseBody(objectMapper, properties);
    }

}
