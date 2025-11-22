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
public class LedgerEntry {

    public static final String OWNER_TYPE_USER = "USER";
    public static final String OWNER_TYPE_PLATFORM = "PLATFORM";
    public static final String ENTRY_DIRECTION_CREDIT = "CREDIT";
    public static final String ENTRY_DIRECTION_DEBIT = "DEBIT";
    public static final String ENTRY_TYPE_DEPOSIT = "DEPOSIT";
    public static final String ENTRY_TYPE_WITHDRAWAL = "WITHDRAWAL";

    public static final String OWNER_TYPE_USER = "USER";
    public static final String OWNER_TYPE_PLATFORM = "PLATFORM";

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
