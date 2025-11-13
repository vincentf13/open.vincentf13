package open.vincentf13.exchange.order.infra.persistence.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import open.vincentf13.exchange.order.domain.model.OrderEventType;

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
    private OrderEventType eventType;
    private Long sequenceNumber;
    private String payload;
    private Long referenceId;
    private String referenceType;
    private String actor;
    private Instant occurredAt;
    private Instant createdAt;
}
