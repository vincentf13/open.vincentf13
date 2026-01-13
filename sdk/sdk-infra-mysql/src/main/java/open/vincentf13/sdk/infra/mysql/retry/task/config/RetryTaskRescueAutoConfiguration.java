package open.vincentf13.sdk.infra.mysql.retry.task.config;

import java.time.Duration;
import open.vincentf13.sdk.infra.mysql.retry.task.RetryTaskRescueScheduler;
import open.vincentf13.sdk.infra.mysql.retry.task.RetryTaskService;
import open.vincentf13.sdk.infra.mysql.retry.task.repository.RetryTaskMapper;
import open.vincentf13.sdk.infra.mysql.retry.task.repository.RetryTaskRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@AutoConfiguration
@EnableScheduling
@ConditionalOnClass(Scheduled.class)
@ConditionalOnProperty(
    prefix = "open.vincentf13.retry-task.rescue",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class RetryTaskRescueAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public RetryTaskRepository retryTaskRepository(RetryTaskMapper mapper) {
    return new RetryTaskRepository(mapper);
  }

  @Bean
  @ConditionalOnMissingBean
  public RetryTaskService retryTaskRescueService(RetryTaskRepository repository) {
    return new RetryTaskService(repository);
  }

  @Bean
  public RetryTaskRescueScheduler retryTaskRescueScheduler(
      RetryTaskService rescueService,
      @Value("${open.vincentf13.retry-task.rescue.timeout:PT10M}") Duration timeout) {
    return new RetryTaskRescueScheduler(rescueService, timeout);
  }
}
