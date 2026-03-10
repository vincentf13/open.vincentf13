package open.vincentf13.exchange.matching.infra.wal;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import open.vincentf13.exchange.matching.domain.match.result.MatchResult;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalEntry {
  private long seq;
  private MatchResult matchResult;
  private Instant appendedAt;
}
