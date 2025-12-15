package open.vincentf13.exchange.matching.infra.wal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import open.vincentf13.exchange.matching.domain.model.MatchingOrder;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SnapshotState {
    private long lastSeq;
    private List<MatchingOrder> openOrders;
    private Instant createdAt;
}
