package open.vincentf13.sdk.infra.mysql.mq.outbox;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MqOutboxPO {

    private String eventId;
    private String aggregateType;
    private Long aggregateId;
    private String eventType;
    private String payload;
    private String headers;
    private Long seq;
    private Instant createdAt;
}
