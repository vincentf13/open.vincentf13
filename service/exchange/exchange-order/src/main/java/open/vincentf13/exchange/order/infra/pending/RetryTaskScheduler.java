package open.vincentf13.exchange.order.infra.pending;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.order.infra.pending.dto.OrderPrepareIntentPayload;
import open.vincentf13.exchange.order.service.OrderCommandService;
import open.vincentf13.sdk.core.values.OpenEnum;
import open.vincentf13.sdk.core.object.mapper.OpenObjectMapper;
import open.vincentf13.sdk.infra.mysql.retry.task.RetryTaskProcessResult;
import open.vincentf13.sdk.infra.mysql.retry.task.RetryTaskService;
import open.vincentf13.sdk.infra.mysql.retry.task.repository.RetryTaskPO;
import open.vincentf13.sdk.infra.mysql.retry.task.repository.RetryTaskRepository;
import open.vincentf13.sdk.infra.mysql.retry.task.repository.RetryTaskStatus;
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
    private final OrderCommandService orderCommandService;
    
    @Value("${open.vincentf13.exchange.order.pending.prepare-intent.retry-delay:PT10S}")
    private Duration retryDelay;

    @Value("${open.vincentf13.exchange.order.pending.prepare-intent.fetch-limit:10}")
    private int fetchLimit;

    @Scheduled(fixedDelayString = "${open.vincentf13.exchange.order.pending.prepare-intent.fixed-delay-ms:5000}")
    public void processPendingPrepareIntent() {
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
                case ORDER_PREPARE_INTENT -> retryTaskService.handleTask(
                        task,
                        retryDelay,
                        retryTask -> {
                            OrderPrepareIntentPayload payload = OpenObjectMapper.fromJson(retryTask.getPayload(), OrderPrepareIntentPayload.class);
                            if (payload == null || payload.getOrderId() == null || payload.getUserId() == null || payload.getRequest() == null) {
                                return new RetryTaskProcessResult(RetryTaskStatus.FAIL_TERMINAL, "invalidPayload");
                            }
                            return orderCommandService.retryPrepareIntentAndHandle(payload, null);
                        }
                );
            }
        }
    }
}
