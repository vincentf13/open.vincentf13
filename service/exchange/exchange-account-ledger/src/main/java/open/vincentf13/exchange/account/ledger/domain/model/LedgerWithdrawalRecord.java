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
public class LedgerWithdrawalRecord {

    private Long withdrawalId;
    private Long reservedEntryId;
    private String status;
    private BigDecimal balanceAfter;
    private Instant requestedAt;
    private Long userId;
    private String asset;
    private BigDecimal amount;
    private BigDecimal fee;
    private String externalRef;
}
