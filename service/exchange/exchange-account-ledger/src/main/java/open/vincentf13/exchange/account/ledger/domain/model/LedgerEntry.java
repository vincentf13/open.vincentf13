package open.vincentf13.exchange.account.ledger.domain.model;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import open.vincentf13.exchange.common.sdk.constants.ValidationConstant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.enums.*;
import open.vincentf13.exchange.common.sdk.enums.AssetSymbol;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerEntry {

    @NotNull
    private Long entryId;
    @NotNull
    private OwnerType ownerType;
    @NotNull
    private Long accountId;
    private Long userId;
    @NotNull
    private AssetSymbol asset;
    @NotNull
    @DecimalMin(value = ValidationConstant.Names.ZERO, inclusive = true)
    private BigDecimal amount;
    @NotNull
    private Direction direction;
    private Long counterpartyEntryId;
    private BigDecimal balanceAfter;
    @NotNull
    private ReferenceType referenceType;
    private String referenceId;
    @NotNull
    private EntryType entryType;
    private String description;
    private String metadata;
    @NotNull
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
                                          Instant eventTime) {
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
                eventTime);
    }

    public static LedgerEntry platformDeposit(Long entryId,
                                              Long accountId,
                                              AssetSymbol asset,
                                              BigDecimal amount,
                                              Long counterpartyEntryId,
                                              BigDecimal balanceAfter,
                                              String referenceId,
                                              Instant eventTime) {
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
                eventTime);
    }

    public static LedgerEntry userWithdrawal(Long entryId,
                                             Long accountId,
                                             Long userId,
                                             AssetSymbol asset,
                                             BigDecimal amount,
                                             BigDecimal balanceAfter,
                                             String referenceId,
                                             String metadata,
                                             Instant eventTime) {
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
                .build();
    }

    public static LedgerEntry platformWithdrawal(Long entryId,
                                                 Long accountId,
                                                 AssetSymbol asset,
                                                 BigDecimal amount,
                                                 Long counterpartyEntryId,
                                                 BigDecimal balanceAfter,
                                                 String referenceId,
                                                 Instant eventTime) {
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
                .build();
    }

    public static LedgerEntry userFundsFreeze(Long entryId,
                                              Long accountId,
                                              Long userId,
                                              AssetSymbol asset,
                                              BigDecimal amount,
                                              BigDecimal balanceAfter,
                                              Long orderId,
                                              Long counterpartyEntryId,
                                              Instant eventTime) {
        return LedgerEntry.builder()
                .entryId(entryId)
                .ownerType(OwnerType.USER)
                .accountId(accountId)
                .userId(userId)
                .asset(asset)
                .amount(amount)
                .direction(Direction.DEBIT)
                .counterpartyEntryId(counterpartyEntryId)
                .balanceAfter(balanceAfter)
                .referenceType(EntryType.FREEZE.referenceType())
                .referenceId(orderId == null ? null : orderId.toString())
                .entryType(EntryType.FREEZE)
                .description("Funds frozen for order")
                .eventTime(eventTime)
                .build();
    }

    public static LedgerEntry userFundsReserved(Long entryId,
                                                Long accountId,
                                                Long userId,
                                                AssetSymbol asset,
                                                BigDecimal amount,
                                                BigDecimal balanceAfter,
                                                Long orderId,
                                                Long counterpartyEntryId,
                                                Instant eventTime) {
        return LedgerEntry.builder()
                .entryId(entryId)
                .ownerType(OwnerType.USER)
                .accountId(accountId)
                .userId(userId)
                .asset(asset)
                .amount(amount)
                .direction(Direction.CREDIT)
                .counterpartyEntryId(counterpartyEntryId)
                .balanceAfter(balanceAfter)
                .referenceType(EntryType.RESERVED.referenceType())
                .referenceId(orderId == null ? null : orderId.toString())
                .entryType(EntryType.RESERVED)
                .description("Reserved balance increased for order")
                .eventTime(eventTime)
                .build();
    }

    public static LedgerEntry tradeSettlement(Long entryId,
                                              Long accountId,
                                              Long userId,
                                              AssetSymbol asset,
                                              BigDecimal amount,
                                              BigDecimal balanceAfter,
                                              Long tradeId,
                                              Long orderId,
                                              Long instrumentId,
                                              Instant eventTime) {
        return LedgerEntry.builder()
                .entryId(entryId)
                .ownerType(OwnerType.USER)
                .accountId(accountId)
                .userId(userId)
                .asset(asset)
                .amount(amount)
                .direction(Direction.DEBIT)
                .balanceAfter(balanceAfter)
                .referenceType(EntryType.TRADE.referenceType())
                .referenceId(tradeId == null ? null : tradeId.toString())
                .entryType(EntryType.TRADE)
                .description("Trade executed for order " + orderId + " instrument " + instrumentId)
                .eventTime(eventTime)
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
                                       Instant eventTime) {
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
                .build();
    }
}
