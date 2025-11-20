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
public class LedgerEntryPO {

    private Long entryId;
    private String ownerType;
    private Long accountId;
    private Long userId;
    private String asset;
    private BigDecimal amount;
    private String direction;
    private Long counterpartyEntryId;
    private BigDecimal balanceAfter;
    private String referenceType;
    private Long referenceId;
    private String entryType;
    private String description;
    private String metadata;
    private Instant eventTime;
    private Instant createdAt;
}
