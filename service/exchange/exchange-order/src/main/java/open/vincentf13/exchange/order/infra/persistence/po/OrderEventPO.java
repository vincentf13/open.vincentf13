package open.vincentf13.exchange.order.infra.persistence.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderEventPO {
    private Long eventId;
    private Long userId;
    private Long instrumentId;
    private Long orderId;
    private String eventType;
    private Long sequenceNumber;
    private String payload;
    private Long referenceId;
    private String referenceType;
    private String actor;
    private Instant occurredAt;
    private Instant createdAt;
}
