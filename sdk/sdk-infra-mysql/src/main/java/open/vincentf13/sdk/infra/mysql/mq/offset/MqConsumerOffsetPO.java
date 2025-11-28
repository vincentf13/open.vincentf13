package open.vincentf13.sdk.infra.mysql.mq.offset;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MqConsumerOffsetPO {
    
    private String consumerGroup;
    private String topic;
    private Integer partitionId;
    private Long lastSeq;
    private Instant updatedAt;
}
