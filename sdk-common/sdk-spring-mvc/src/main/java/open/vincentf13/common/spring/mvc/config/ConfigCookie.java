package open.vincentf13.common.spring.mvc.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ConfigCookie {
    @Bean
    ServletContextInitializer sessionCookieMaxAge() {
        return sc -> sc.getSessionCookieConfig().setMaxAge(30 * 60); // 30 分鐘 }
    }

}