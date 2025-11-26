package open.vincentf13.exchange.account.ledger.infra.persistence.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.enums.Direction;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.enums.EntryType;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.enums.OwnerType;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.enums.ReferenceType;
import open.vincentf13.exchange.common.sdk.enums.AssetSymbol;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerEntryPO {

    private Long entryId;
    private OwnerType ownerType;
    private Long accountId;
    private Long userId;
    private AssetSymbol asset;
    private BigDecimal amount;
    private Direction direction;
    private Long counterpartyEntryId;
    private BigDecimal balanceAfter;
    private ReferenceType referenceType;
    private String referenceId;
    private EntryType entryType;
    private String description;
    private String metadata;
    private Instant eventTime;
    private Instant createdAt;
}
