package open.vincentf13.sdk.devtool.kafka.consumer.controller;


import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.sdk.auth.auth.PublicAPI;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AlterConsumerGroupOffsetsResult;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsResult;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.ConsumerGroupState;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/infra/kafka")
@RequiredArgsConstructor
public class KafkaAdminController {

    private final KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;
    private final KafkaAdmin kafkaAdmin;

    /**
       動態調整 Kafka Consumer 的 Offset (Seek)。

       此接口允許在服務運行時，將指定的 Topic Partition 重置到任意 Offset。
       由於 Spring Kafka 容器限制，此操作會暫時停止並重啟相關的 Listener Container。

       參數詳細說明：
       <table>
         <thead>
           <tr>
             <th>參數名稱</th>
             <th>類型</th>
             <th>必填</th>
             <th>作用說明</th>
           </tr>
         </thead>
         <tbody>
           <tr>
             <td>listenerId</td>
             <td>String</td>
             <td>否</td>
             <td>監聽器 ID。Spring Kafka 中 @KafkaListener 指定的 id。如果提供，則僅針對該特定的 Consumer 執行調整；如果不提供，則會掃描所有 Container。</td>
           </tr>
           <tr>
             <td>topic</td>
             <td>String</td>
             <td>是</td>
             <td>主題名稱。僅填寫 Topic 名稱，**不可**包含分區後綴。</td>
           </tr>
           <tr>
             <td>partition</td>
             <td>Integer</td>
             <td>是</td>
             <td>分區編號。指定該 Topic 下哪一個分區要進行調整（從 0 開始）。</td>
           </tr>
           <tr>
             <td>offset</td>
             <td>Long</td>
             <td>否</td>
             <td>目標位置。若為 null，則自動重置為「當前 Committed Offset - 1」（回退一格）。</td>
           </tr>
         </tbody>
       </table>

       API 請求範本 (.http):
       ### 重置 Kafka Offset 範例 (指定 Offset)
       POST http://localhost:8080/infra/kafka/seek
       Content-Type: application/json

       {
         "topic": "exchange.order",
         "partition": 0,
         "offset": 100
       }

       ### 重置 Kafka Offset 範例 (回退一格)
       POST http://localhost:8080/infra/kafka/seek
       Content-Type: application/json

       {
         "topic": "exchange.order",
         "partition": 0
       }
     */
    @PostMapping("/seek")
    @PublicAPI
    public ApiResponse<List<String>> seekOffset(@RequestBody KafkaSeekRequest request) {
        log.info("收到 Kafka Offset 調整請求: {}", request);
        List<String> results = new ArrayList<>();

        Collection<MessageListenerContainer> containers = kafkaListenerEndpointRegistry.getListenerContainers();
        List<MessageListenerContainer> targetContainers = new ArrayList<>();
        for (MessageListenerContainer container : containers) {
            if (request.getListenerId() != null && !request.getListenerId().isEmpty()) {
                if (!request.getListenerId().equals(container.getListenerId())) {
                    continue;
                }
            }

            boolean listensToTopic = false;
            if (container.getContainerProperties().getTopics() != null) {
                for (String t : container.getContainerProperties().getTopics()) {
                    if (t.equals(request.getTopic())) {
                        listensToTopic = true;
                        break;
                    }
                }
            }
            if (listensToTopic) {
                targetContainers.add(container);
            }
        }

        if (targetContainers.isEmpty()) {
            return ApiResponse.error("未找到匹配目標 Topic 的監聽器容器，或執行過程中無任何操作。");
        }

        String groupId = targetContainers.get(0).getGroupId();
        if (groupId == null || groupId.isEmpty()) {
            return ApiResponse.error("目標監聽器容器無 Group ID，無法調整 Offset。");
        }

        List<MessageListenerContainer> groupContainers = new ArrayList<>();
        for (MessageListenerContainer container : containers) {
            if (groupId.equals(container.getGroupId())) {
                groupContainers.add(container);
            }
        }
        
        try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            try {
                CountDownLatch stopLatch = new CountDownLatch(groupContainers.size());
                for (MessageListenerContainer container : groupContainers) {
                    log.info("正在停止 Container: {}", container.getListenerId());
                    container.stop(stopLatch::countDown);
                }
                if (!stopLatch.await(10, TimeUnit.SECONDS)) {
                    log.warn("等待 Container 停止超時 (groupId={})", groupId);
                }

                TopicPartition tp = new TopicPartition(request.getTopic(), request.getPartition());
                long targetOffset;

                // 3. 計算目標 Offset
                if (request.getOffset() == null) {
                    ListConsumerGroupOffsetsResult offsetsResult = adminClient.listConsumerGroupOffsets(groupId);
                    Map<TopicPartition, OffsetAndMetadata> committedOffsets = offsetsResult.partitionsToOffsetAndMetadata().get();
                    OffsetAndMetadata current = committedOffsets.get(tp);

                    if (current == null) {
                        throw new IllegalStateException("無法取得當前 Committed Offset，無法執行回退操作。");
                    }

                    targetOffset = Math.max(0, current.offset() - 1);
                    log.info("Offset 未帶，根據 Committed Offset {} 回退至 {}", current.offset(), targetOffset);
                } else {
                    targetOffset = request.getOffset();
                }

                // 4. 修改 Offset
                for (int i = 0; i < 10; i++) {
                    try {
                        ConsumerGroupDescription description = adminClient.describeConsumerGroups(Collections.singletonList(groupId))
                                                                          .describedGroups()
                                                                          .get(groupId)
                                                                          .get();
                        ConsumerGroupState state = description.state();
                        if (state == ConsumerGroupState.EMPTY || state == ConsumerGroupState.DEAD || description.members().isEmpty()) {
                            break;
                        }
                        log.warn("Consumer group 狀態尚未穩定，等待後再調整 offset (state={})", state);
                    } catch (ExecutionException e) {
                        log.warn("取得 consumer group 狀態失敗，等待後再調整 offset", e.getCause());
                    }
                    Thread.sleep(500L);
                }
                Map<TopicPartition, OffsetAndMetadata> newOffsets = Collections.singletonMap(
                        tp, new OffsetAndMetadata(targetOffset)
                );
                int attempt = 0;
                while (true) {
                    try {
                        AlterConsumerGroupOffsetsResult alterResult = adminClient.alterConsumerGroupOffsets(groupId, newOffsets);
                        alterResult.all().get();
                        break;
                    } catch (ExecutionException e) {
                        if ((e.getCause() instanceof org.apache.kafka.common.errors.UnknownMemberIdException
                                || e.getCause() instanceof org.apache.kafka.common.errors.RebalanceInProgressException)
                                && attempt < 10) {
                            attempt++;
                            log.warn("Consumer group 狀態變更中，準備重試調整 offset (attempt={})", attempt, e.getCause());
                            Thread.sleep(1000L);
                            continue;
                        }
                        throw e;
                    }
                }

                String msg = String.format("Group %s 已重置 %s-%d 至 Offset %d",
                        groupId, request.getTopic(), request.getPartition(), targetOffset);
                results.add(msg);
                log.info(msg);

            } catch (InterruptedException | ExecutionException e) {
                String errorMsg = String.format("Group %s 修改 Offset 失敗: %s", groupId, e.getMessage());
                results.add(errorMsg);
                log.error(errorMsg, e);
            } finally {
                for (MessageListenerContainer container : groupContainers) {
                    log.info("正在重啟 Container: {}", container.getListenerId());
                    container.start();
                }
            }
        }

        return ApiResponse.success(results);
    }
    
    // Internal simple API response structure to avoid circular dependencies
    public record ApiResponse<T>(
            String code,
            String message,
            T data,
            Instant timestamp
    ) {
        public static <T> ApiResponse<T> success(T data) {
            return new ApiResponse<>("0", "OK", data, Instant.now());
        }

        public static <T> ApiResponse<T> error(String message) {
            return new ApiResponse<>("-1", message, null, Instant.now());
        }
    }

    @Data
    public static class KafkaSeekRequest {
        /**
           可選：監聽器 ID。如果提供，則僅針對該監聽器執行 seek 操作。
         */
        private String listenerId;
        
        /**
           主題 (Topic) 名稱。注意：僅填寫名稱，**不可**包含 "-0" 等分區後綴。
         */
        private String topic;
        
        /**
           分區 (Partition) 編號
         */
        private int partition;
        
        /**
           目標 Offset。若為 null，則嘗試移至當前 Committed Offset - 1 (回退一格)。
         */
        private Long offset;
    }
}
