package open.vincentf13.exchange.order.infra.pending.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import open.vincentf13.exchange.order.sdk.rest.dto.OrderCreateRequest;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderPrepareIntentPayload {

  private Long orderId;
  private Long userId;
  private OrderCreateRequest request;
}
