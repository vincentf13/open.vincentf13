package open.vincentf13.exchange.account.ledger.infra.persistence.po;

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
public class LedgerBalancePO {

    private Long id;
    private Long accountId;
    private Long userId;
    private String accountType;
    private Long instrumentId;
    private String asset;
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
