package open.vincentf13.exchange.account.ledger.infra.persistence.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.enums.AccountType;
import open.vincentf13.exchange.sdk.common.enums.AssetSymbol;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerBalancePO {

    private Long id;
    private Long accountId;
    private Long userId;
    private AccountType accountType;
    private Long instrumentId;
    private AssetSymbol asset;
    private BigDecimal balance;
    private BigDecimal available;
    private BigDecimal reserved;
    private BigDecimal totalDeposited;
    private BigDecimal totalWithdrawn;
    private BigDecimal totalPnl;
    private Integer version;
    private Long lastEntryId;
    private Instant createdAt;
    private Instant updatedAt;
}
