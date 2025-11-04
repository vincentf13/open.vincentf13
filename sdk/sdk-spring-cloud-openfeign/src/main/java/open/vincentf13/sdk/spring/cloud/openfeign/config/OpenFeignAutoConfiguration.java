package open.vincentf13.sdk.spring.cloud.openfeign.config;

import feign.FeignException;
import open.vincentf13.sdk.spring.cloud.openfeign.FeignExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.RestTemplate;

@AutoConfiguration
@ConditionalOnClass(FeignException.class)
@Import(FeignExceptionHandler.class)
public class OpenFeignAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
