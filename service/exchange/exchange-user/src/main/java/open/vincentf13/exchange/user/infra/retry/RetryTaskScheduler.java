package open.vincentf13.exchange.user.infra.retry;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.user.infra.retry.dto.AuthCredentialCreatePayload;
import open.vincentf13.exchange.user.service.UserCommandService;
import open.vincentf13.sdk.core.object.mapper.OpenObjectMapper;
import open.vincentf13.sdk.core.values.OpenEnum;
import open.vincentf13.sdk.infra.mysql.retry.task.RetryTaskService;
import open.vincentf13.sdk.infra.mysql.retry.task.repository.RetryTaskPO;
import open.vincentf13.sdk.infra.mysql.retry.task.repository.RetryTaskRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RetryTaskScheduler {
    
    private static final int DEFAULT_FETCH_LIMIT = 10;
    
    private final RetryTaskRepository retryTaskRepository;
    private final RetryTaskService retryTaskService;
    private final UserCommandService userCommandService;
    
    @Value("${open.vincentf13.exchange.user.auth.retry-delay:PT60S}")
    private Duration retryDelay;
    
    @Value("${open.vincentf13.exchange.user.auth.retry.fetch-limit:10}")
    private int fetchLimit;
    
    @Scheduled(fixedDelayString = "${open.vincentf13.exchange.user.auth.retry.fixed-delay-ms:5000}")
    public void retryAuthCredentialTasks() {
        int limit = fetchLimit > 0 ? fetchLimit : DEFAULT_FETCH_LIMIT;
        List<RetryTaskPO> tasks = retryTaskRepository.findPending(limit);
        for (RetryTaskPO task : tasks) {
            RetryTaskType taskType = OpenEnum.resolve(task.getBizType(), RetryTaskType.class);
            if (taskType == null) {
                continue;
            }
            if (!retryTaskRepository.tryMarkProcessing(task.getId(), task.getVersion())) {
                continue;
            }
            switch (taskType) {
                case AUTH_CREDENTIAL_CREATE -> retryTaskService.handleTask(task, retryDelay, retryTask -> {
                    return userCommandService.createAuthCredential(OpenObjectMapper.fromJson(retryTask.getPayload(), AuthCredentialCreatePayload.class));
                });
            }
        }
    }
}
