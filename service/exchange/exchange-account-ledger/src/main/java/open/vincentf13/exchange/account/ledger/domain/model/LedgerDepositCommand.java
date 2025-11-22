package open.vincentf13.exchange.account.ledger.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerDepositCommand {

    private Long userId;
    private String asset;
    private BigDecimal amount;
    private LedgerBalanceAccountType accountType;
    private Long instrumentId;
    private String txId;
    private String channel;
    private Instant creditedAt;
    private String metadata;
}
