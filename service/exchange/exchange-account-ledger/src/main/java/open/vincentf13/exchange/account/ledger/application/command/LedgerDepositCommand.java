package open.vincentf13.exchange.account.ledger.application.command;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.dto.LedgerBalanceAccountType;

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
    private String txId;
    private Instant creditedAt;
    private LedgerBalanceAccountType accountType;
}
