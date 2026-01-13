package open.vincentf13.sdk.spring.cloud.openfeign.log.config;

import feign.Logger;
import open.vincentf13.sdk.spring.cloud.openfeign.log.OpenFeignLogger;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class FeignLogAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean(Logger.class)
  public Logger feignLogger() {
    return new OpenFeignLogger();
  }

  @Bean
  @ConditionalOnMissingBean(Logger.Level.class)
  public Logger.Level feignLoggerLevel() {
    return Logger.Level.FULL;
  }
}
