package open.vincentf13.exchange.matching.infra.snapshot;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import open.vincentf13.exchange.matching.domain.order.book.Order;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SnapshotState {
    private long lastSeq;
    private List<Order> openOrders;
    private Map<Long, Order> orderMap;
    private Long kafkaOffset;
    private Instant createdAt;
}
