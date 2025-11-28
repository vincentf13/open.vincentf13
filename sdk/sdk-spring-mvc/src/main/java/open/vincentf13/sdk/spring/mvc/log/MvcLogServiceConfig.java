package open.vincentf13.sdk.spring.mvc.log;

import open.vincentf13.sdk.spring.mvc.log.MvcLogService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@AutoConfiguration
@ConditionalOnClass(WebMvcConfigurer.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class MvcLogServiceConfig {

    /*
     * 提供 MVC 請求/回應日誌服務。
     */
    @Bean
    public MvcLogService mvcLogService() {
        return new MvcLogService();
    }
}
