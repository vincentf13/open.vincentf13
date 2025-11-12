package open.vincentf13.exchange.order.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderEvent {
    private Long eventId;
    private Long orderId;
    private Long userId;
    private Long instrumentId;
    private OrderEventType eventType;
    private String payload;
    private Long referenceId;
    private String referenceType;
    private String actor;
    private Instant occurredAt;
    private Long sequenceNumber;
    private Instant createdAt;
}
