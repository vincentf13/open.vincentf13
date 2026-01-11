package open.vincentf13.sdk.infra.kafka.consumer.controller.dto;

import lombok.Data;

@Data
public class KafkaSeekRequest {
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
