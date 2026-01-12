package open.vincentf13.sdk.infra.mysql.retry.task.config;

import open.vincentf13.sdk.infra.mysql.retry.task.RetryTaskRescueScheduler;
import open.vincentf13.sdk.infra.mysql.retry.task.RetryTaskRescueService;
import open.vincentf13.sdk.infra.mysql.retry.task.repository.RetryTaskMapper;
import open.vincentf13.sdk.infra.mysql.retry.task.repository.RetryTaskRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;

@AutoConfiguration
@EnableScheduling
@ConditionalOnClass(Scheduled.class)
@ConditionalOnProperty(prefix = "open.vincentf13.pending-task.rescue", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RetryTaskRescueAutoConfiguration {
    
    @Bean
    @ConditionalOnMissingBean
    public RetryTaskRepository retryTaskRepository(RetryTaskMapper mapper) {
        return new RetryTaskRepository(mapper);
    }
    
    @Bean
    @ConditionalOnMissingBean
    public RetryTaskRescueService retryTaskRescueService(RetryTaskRepository repository) {
        return new RetryTaskRescueService(repository);
    }
    
    @Bean
    public RetryTaskRescueScheduler retryTaskRescueScheduler(RetryTaskRescueService rescueService,
                                                             @Value("${open.vincentf13.pending-task.rescue.timeout:PT10M}") Duration timeout) {
        return new RetryTaskRescueScheduler(rescueService, timeout);
    }
}
