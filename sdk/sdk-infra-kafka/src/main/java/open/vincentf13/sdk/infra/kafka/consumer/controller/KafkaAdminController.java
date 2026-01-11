package open.vincentf13.sdk.infra.kafka.consumer.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.sdk.infra.kafka.consumer.controller.dto.KafkaSeekRequest;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AlterConsumerGroupOffsetsResult;
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsResult;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

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
       ┌────────────┬─────────┬──────┬──────────────────────────────────────────────────────────────────────────────────────────┐
       │ 參數名稱   │ 類型    │ 必填 │ 作用說明                                                                                 │
       ├────────────┼─────────┼──────┼──────────────────────────────────────────────────────────────────────────────────────────┤
       │ listenerId │ String  │ 否   │ 監聽器 ID。Spring Kafka 中 @KafkaListener 指定的 id。如果提供，則僅針對該特定的 Consumer │
       │            │         │      │ 執行調整；如果不提供，則會掃描所有 Container。                                           │
       ├────────────┼─────────┼──────┼──────────────────────────────────────────────────────────────────────────────────────────┤
       │ topic      │ String  │ 是   │ 主題名稱。僅填寫 Topic 名稱，**不可**包含分區後綴。                                      │
       ├────────────┼─────────┼──────┼──────────────────────────────────────────────────────────────────────────────────────────┤
       │ partition  │ Integer │ 是   │ 分區編號。指定該 Topic 下哪一個分區要進行調整（從 0 開始）。                             │
       ├────────────┼─────────┼──────┼──────────────────────────────────────────────────────────────────────────────────────────┤
       │ offset     │ Long    │ 否   │ 目標位置。若為 null，則自動重置為「當前 Committed Offset - 1」（回退一格）。             │
       └────────────┴─────────┴──────┴──────────────────────────────────────────────────────────────────────────────────────────┘

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
    public ApiResponse<List<String>> seekOffset(@RequestBody KafkaSeekRequest request) {
        log.info("收到 Kafka Offset 調整請求: {}", request);
        List<String> results = new ArrayList<>();

        Collection<MessageListenerContainer> containers = kafkaListenerEndpointRegistry.getListenerContainers();
        
        try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            
            for (MessageListenerContainer container : containers) {
                // 1. 過濾 Listener ID
                if (request.getListenerId() != null && !request.getListenerId().isEmpty()) {
                    if (!request.getListenerId().equals(container.getListenerId())) {
                        continue;
                    }
                }

                // 2. 過濾 Topic (檢查該 Container 是否監聽目標 Topic)
                boolean listensToTopic = false;
                if (container.getContainerProperties().getTopics() != null) {
                    for (String t : container.getContainerProperties().getTopics()) {
                        if (t.equals(request.getTopic())) {
                            listensToTopic = true;
                            break;
                        }
                    }
                }
                // 也要檢查 Pattern 或 TopicPartitions 設定，但簡化起見先檢查 Topics
                if (!listensToTopic) {
                    continue;
                }

                String groupId = container.getGroupId();
                if (groupId == null || groupId.isEmpty()) {
                    results.add(String.format("跳過 Container %s: 無 Group ID", container.getListenerId()));
                    continue;
                }

                try {
                    log.info("正在停止 Container: {}", container.getListenerId());
                    container.stop();

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
                    Map<TopicPartition, OffsetAndMetadata> newOffsets = Collections.singletonMap(
                            tp, new OffsetAndMetadata(targetOffset)
                    );
                    AlterConsumerGroupOffsetsResult alterResult = adminClient.alterConsumerGroupOffsets(groupId, newOffsets);
                    alterResult.all().get();

                    String msg = String.format("Container %s (Group: %s) 已重置 %s-%d 至 Offset %d",
                            container.getListenerId(), groupId, request.getTopic(), request.getPartition(), targetOffset);
                    results.add(msg);
                    log.info(msg);

                } catch (InterruptedException | ExecutionException e) {
                    String errorMsg = String.format("Container %s 修改 Offset 失敗: %s", container.getListenerId(), e.getMessage());
                    results.add(errorMsg);
                    log.error(errorMsg, e);
                    // 若失敗仍嘗試重啟，避免服務中斷
                } finally {
                    log.info("正在重啟 Container: {}", container.getListenerId());
                    container.start();
                }
            }
        }

        if (results.isEmpty()) {
            return ApiResponse.error("未找到匹配目標 Topic 的監聽器容器，或執行過程中無任何操作。");
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
}
