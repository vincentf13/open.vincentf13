package open.vincentf13.sdk.infra.mysql.mq.deadletter;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MqDeadLetterPO {
    
    private Long id;
    private String source;
    private Long seq;
    private Long outboxId;
    private String payload;
    private String error;
    private Instant createdAt;
}
