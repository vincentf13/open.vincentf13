package open.vincentf13.exchange.matching.infra.messaging.consumer;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.matching.domain.order.book.Order;
import open.vincentf13.exchange.matching.service.MatchingEngine;
import open.vincentf13.exchange.order.mq.event.OrderCreatedEvent;
import open.vincentf13.exchange.order.mq.topic.OrderTopics;
import open.vincentf13.sdk.core.OpenValidator;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.ConsumerSeekAware;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class OrderCreatedEventListener implements ConsumerSeekAware {
    
    private final MatchingEngine matchingEngine;
    
    /**
     實務上，應該每個 partition 只接收一種 instrument_id
     並且 matching 服務 應該啟多個，每個只單線程消費單一 partition
     */
    @KafkaListener(topics = OrderTopics.Names.ORDER_CREATED,
                   groupId = "${open.vincentf13.exchange.matching.consumer-group:exchange-matching}",
                   concurrency = "1")
    public void onOrderCreated(@Payload OrderCreatedEvent event,
                               @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                               @Header(KafkaHeaders.OFFSET) long offset,
                               Acknowledgment acknowledgment) {
        OpenValidator.validateOrThrow(event);
        Order order = Order.builder()
                           .orderId(event.orderId())
                           .userId(event.userId())
                           .instrumentId(event.instrumentId())
                           .clientOrderId(event.clientOrderId())
                           .side(event.side())
                           .type(event.type())
                           .intent(event.intent())
                           .price(event.price())
                           .quantity(event.quantity())
                           .submittedAt(event.submittedAt())
                           .build();
        matchingEngine.process(order, partition, offset);
        acknowledgment.acknowledge();
    }
    
    /**
     onPartitionsAssigned 會帶進來 Kafka 當前給「這個 consumer 實例」分配到的 partitions；
     分配結果是 Kafka rebalance 算出來的（受 groupId、併發數、集群分區數、其他同 group 實例數影響），在程式啟動或 rebalance 時才知道是哪幾個分區。
   
     如果要「固定只消費指定分區」，可以改用 @KafkaListener(topicPartitions = @TopicPartition(topic = "...", partitions ={"0"}))或在容器設定手動分配；
     否則預設就是 Kafka 決定並透過 assignments 告訴我們這個實例拿到哪些分區。
     
     */
    @Override
    public void onPartitionsAssigned(Map<TopicPartition, Long> assignments,
                                     ConsumerSeekCallback callback) {
        // 單線程引擎：按快照／WAL 記錄的偏移重新定位，避免重複或跳過
        assignments.keySet().forEach(tp -> {
            long nextOffset = matchingEngine.offsetForPartition(tp.partition()) + 1;
            if (nextOffset > 0) {
                callback.seek(tp.topic(), tp.partition(), nextOffset);
            }
        });
    }
}
