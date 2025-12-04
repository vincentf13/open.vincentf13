package open.vincentf13.sdk.infra.mysql.pending;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;

@AutoConfiguration
@EnableScheduling
@ConditionalOnClass(Scheduled.class)
@ConditionalOnProperty(prefix = "open.vincentf13.pending-task.rescue", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SysPendingTaskRescueAutoConfiguration {
    
    @Bean
    public SysPendingTaskRescueScheduler sysPendingTaskRescueScheduler(SysPendingTaskRescueService rescueService,
                                                                       @Value("${open.vincentf13.pending-task.rescue.timeout:PT10M}") Duration timeout) {
        return new SysPendingTaskRescueScheduler(rescueService, timeout);
    }
}
