package open.vincentf13.sdk.spring.mvc.exception;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@AutoConfiguration
@ConditionalOnClass(WebMvcConfigurer.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class GlobalExceptionHandlerConfig {

  /*
   * 提供預設的 RestExceptionHandler，避免每個服務重複實作。
   */
  @Bean
  @ConditionalOnMissingBean
  public OpenRestExceptionAdvice restExceptionHandler() {
    return new OpenRestExceptionAdvice();
  }
}
