package open.vincentf13.exchange.order.domain.model;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder(toBuilder = true)
public class OrderEvent {
    Long eventId;
    Long userId;
    Long instrumentId;
    Long orderId;
    String eventType;
    Long sequenceNumber;
    String payload;
    Long referenceId;
    String referenceType;
    String actor;
    Instant occurredAt;
    Instant createdAt;
}
