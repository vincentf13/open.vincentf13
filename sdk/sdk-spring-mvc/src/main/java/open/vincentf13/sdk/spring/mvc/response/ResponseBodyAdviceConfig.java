package open.vincentf13.sdk.spring.mvc.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import open.vincentf13.sdk.spring.mvc.web.MvcProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@AutoConfiguration
@ConditionalOnClass(WebMvcConfigurer.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ResponseBodyAdviceConfig {

    /*
     * 構建 ApiResponseBodyAdvice，統一包裝回應結果。
     */
    @Bean
    @ConditionalOnMissingBean
    public AopResponseBody apiResponseBodyAdvice(ObjectMapper objectMapper, MvcProperties properties) {
        return new AopResponseBody(objectMapper, properties);
    }
}
