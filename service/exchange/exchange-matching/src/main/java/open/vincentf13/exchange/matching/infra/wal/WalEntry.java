package open.vincentf13.exchange.matching.infra.wal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import open.vincentf13.exchange.matching.domain.match.result.MatchResult;
import open.vincentf13.exchange.matching.sdk.mq.event.OrderBookUpdatedEvent;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalEntry {
    private long seq;
    private Long kafkaOffset;
    private Integer partition;
    private MatchResult matchResult;
    private OrderBookUpdatedEvent orderBookUpdatedEvent;
    private Instant appendedAt;
}
