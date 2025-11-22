package open.vincentf13.exchange.account.ledger.application.command;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.dto.LedgerBalanceAccountType;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerWithdrawalCommand {

    private Long userId;
    private String asset;
    private BigDecimal amount;
    private BigDecimal fee;
    private LedgerBalanceAccountType accountType;
    private Long instrumentId;
    private String destination;
    private String externalRef;
    private Instant requestedAt;
    private String metadata;
}
