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
public class LedgerDepositRecord {

    private Long depositId;
    private Long entryId;
    private String status;
    private BigDecimal balanceAfter;
    private Instant creditedAt;
    private Long userId;
    private String asset;
    private BigDecimal amount;
    private String txId;
    private String channel;
}
