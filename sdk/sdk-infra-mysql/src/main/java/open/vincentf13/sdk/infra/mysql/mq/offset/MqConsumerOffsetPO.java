package open.vincentf13.sdk.infra.mysql.mq.offset;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
