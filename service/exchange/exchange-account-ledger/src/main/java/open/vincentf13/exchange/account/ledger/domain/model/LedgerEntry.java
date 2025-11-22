package open.vincentf13.exchange.account.ledger.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.enums.*;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerEntry {

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

    public static LedgerEntry userDeposit(Long entryId,
                                          Long accountId,
                                          Long userId,
                                          AssetSymbol asset,
                                          BigDecimal amount,
                                          Long counterpartyEntryId,
                                          BigDecimal balanceAfter,
                                          String referenceId,
                                          Instant eventTime,
                                          Instant createdAt) {
        return deposit(entryId,
                OwnerType.USER,
                accountId,
                userId,
                asset,
                amount,
                counterpartyEntryId,
                balanceAfter,
                referenceId,
                "用戶充值",
                eventTime,
                createdAt);
    }

    public static LedgerEntry platformDeposit(Long entryId,
                                              Long accountId,
                                              AssetSymbol asset,
                                              BigDecimal amount,
                                              Long counterpartyEntryId,
                                              BigDecimal balanceAfter,
                                              String referenceId,
                                              Instant eventTime,
                                              Instant createdAt) {
        return deposit(entryId,
                OwnerType.PLATFORM,
                accountId,
                null,
                asset,
                amount,
                counterpartyEntryId,
                balanceAfter,
                referenceId,
                "User deposit liability",
                eventTime,
                createdAt);
    }

    public static LedgerEntry userWithdrawal(Long entryId,
                                             Long accountId,
                                             Long userId,
                                             AssetSymbol asset,
                                             BigDecimal amount,
                                             BigDecimal balanceAfter,
                                             String referenceId,
                                             String metadata,
                                             Instant eventTime,
                                             Instant createdAt) {
        return LedgerEntry.builder()
                .entryId(entryId)
                .ownerType(OwnerType.USER)
                .accountId(accountId)
                .userId(userId)
                .asset(asset)
                .amount(amount)
                .direction(Direction.DEBIT)
                .balanceAfter(balanceAfter)
                .referenceType(EntryType.WITHDRAWAL.referenceType())
                .referenceId(referenceId)
                .entryType(EntryType.WITHDRAWAL)
                .description("User withdrawal")
                .metadata(metadata)
                .eventTime(eventTime)
                .createdAt(createdAt)
                .build();
    }

    public static LedgerEntry platformWithdrawal(Long entryId,
                                                 Long accountId,
                                                 AssetSymbol asset,
                                                 BigDecimal amount,
                                                 Long counterpartyEntryId,
                                                 BigDecimal balanceAfter,
                                                 String referenceId,
                                                 Instant eventTime,
                                                 Instant createdAt) {
        return LedgerEntry.builder()
                .entryId(entryId)
                .ownerType(OwnerType.PLATFORM)
                .accountId(accountId)
                .asset(asset)
                .amount(amount)
                .direction(Direction.DEBIT)
                .counterpartyEntryId(counterpartyEntryId)
                .balanceAfter(balanceAfter)
                .referenceType(EntryType.WITHDRAWAL.referenceType())
                .referenceId(referenceId)
                .entryType(EntryType.WITHDRAWAL)
                .description("User withdrawal liability")
                .eventTime(eventTime)
                .createdAt(createdAt)
                .build();
    }

    private static LedgerEntry deposit(Long entryId,
                                       OwnerType ownerType,
                                       Long accountId,
                                       Long userId,
                                       AssetSymbol asset,
                                       BigDecimal amount,
                                       Long counterpartyEntryId,
                                       BigDecimal balanceAfter,
                                       String referenceId,
                                       String description,
                                       Instant eventTime,
                                       Instant createdAt) {
        return LedgerEntry.builder()
                .entryId(entryId)
                .ownerType(ownerType)
                .accountId(accountId)
                .userId(userId)
                .asset(asset)
                .amount(amount)
                .direction(Direction.CREDIT)
                .counterpartyEntryId(counterpartyEntryId)
                .balanceAfter(balanceAfter)
                .referenceType(EntryType.DEPOSIT.referenceType())
                .referenceId(referenceId)
                .entryType(EntryType.DEPOSIT)
                .description(description)
                .eventTime(eventTime)
                .createdAt(createdAt)
                .build();
    }
}
