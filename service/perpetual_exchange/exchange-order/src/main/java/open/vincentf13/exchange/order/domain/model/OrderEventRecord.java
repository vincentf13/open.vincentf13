package open.vincentf13.exchange.order.domain.model;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import open.vincentf13.exchange.order.sdk.rest.api.enums.OrderEventReferenceType;
import open.vincentf13.exchange.order.sdk.rest.api.enums.OrderEventType;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderEventRecord {

  private Long eventId;
  private Long userId;
  private Long instrumentId;
  private Long orderId;
  private OrderEventType eventType;
  private Long sequenceNumber;
  private String payload;
  private OrderEventReferenceType referenceType;
  private String referenceId;
  private String actor;
  private Instant occurredAt;
  private Instant createdAt;
}
