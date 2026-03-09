package open.vincentf13.exchange.matching.infra.snapshot;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import open.vincentf13.exchange.matching.domain.order.book.Order;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SnapshotState {
  private long lastSeq;
  private List<Order> openOrders;
  private List<Long> processedOrderIds;
  private Instant createdAt;
}
