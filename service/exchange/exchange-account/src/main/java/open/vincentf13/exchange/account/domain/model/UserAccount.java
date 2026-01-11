package open.vincentf13.exchange.account.domain.model;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import open.vincentf13.exchange.account.infra.AccountErrorCode;
import open.vincentf13.exchange.account.sdk.rest.api.enums.UserAccountCode;
import open.vincentf13.exchange.account.sdk.rest.api.enums.AccountCategory;
import open.vincentf13.exchange.common.sdk.enums.AssetSymbol;
import open.vincentf13.exchange.common.sdk.enums.Direction;
import open.vincentf13.sdk.core.exception.OpenException;

import open.vincentf13.exchange.account.sdk.rest.api.enums.ReferenceType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAccount {
    
    private Long accountId;
    @NotNull
    private Long userId;
    @NotNull
    private UserAccountCode accountCode;
    private String accountName;
    private Long instrumentId;
    @NotNull
    private AccountCategory category;
    @NotNull
    private AssetSymbol asset;
    @NotNull
    private BigDecimal balance;
    @NotNull
    private BigDecimal available;
    @NotNull
    private BigDecimal reserved;
    private Integer version;
    private Instant createdAt;
    private Instant updatedAt;
    
    public static UserAccount createDefault(Long userId,
                                            UserAccountCode accountCode,
                                            Long instrumentId,
                                            AssetSymbol asset) {
        return UserAccount.builder()
                          .userId(userId)
                          .accountCode(accountCode)
                          .accountName(accountCode.getDisplayName())
                          .instrumentId(instrumentId == null ? 0L : instrumentId)
                          .category(accountCode.getCategory())
                          .asset(asset)
                          .balance(BigDecimal.ZERO)
                          .available(BigDecimal.ZERO)
                          .reserved(BigDecimal.ZERO)
                          .version(0)
                          .build();
    }
    
    public static AssetSymbol normalizeAsset(String asset) {
        return AssetSymbol.fromValue(asset);
    }
    
    public static AssetSymbol normalizeAsset(AssetSymbol asset) {
        if (asset == null) {
            throw OpenException.of(AccountErrorCode.ASSET_REQUIRED);
        }
        return asset;
    }
    
    public int safeVersion() {
        return version == null ? 0 : version;
    }
    
    public UserAccount apply(Direction direction,
                             BigDecimal amount) {
        BigDecimal delta = AccountCategory.delta(category, direction, amount);
        BigDecimal newBalance = balance.add(delta);
        BigDecimal newAvailable = available.add(delta);
        if (newAvailable.signum() < 0) {
            throw OpenException.of(AccountErrorCode.INSUFFICIENT_FUNDS);
        }
        return UserAccount.builder()
                          .accountId(accountId)
                          .userId(userId)
                          .accountCode(accountCode)
                          .accountName(accountName)
                          .instrumentId(instrumentId)
                          .category(category)
                          .asset(asset)
                          .balance(newBalance)
                          .available(newAvailable)
                          .reserved(reserved)
                          .version(safeVersion() + 1)
                          .createdAt(createdAt)
                          .updatedAt(Instant.now())
                          .build();
    }

    public UserAccount applyAllowNegative(Direction direction,
                                          BigDecimal amount) {
        BigDecimal delta = AccountCategory.delta(category, direction, amount);
        BigDecimal newBalance = balance.add(delta);
        BigDecimal newAvailable = available.add(delta);
        return UserAccount.builder()
                          .accountId(accountId)
                          .userId(userId)
                          .accountCode(accountCode)
                          .accountName(accountName)
                          .instrumentId(instrumentId)
                          .category(category)
                          .asset(asset)
                          .balance(newBalance)
                          .available(newAvailable)
                          .reserved(reserved)
                          .version(safeVersion() + 1)
                          .createdAt(createdAt)
                          .updatedAt(Instant.now())
                          .build();
    }

    public UserAccount applyTradeSettlement(BigDecimal totalReserved,
                                            BigDecimal feeRefund) {
        BigDecimal newReserved = reserved.subtract(totalReserved);
        if (newReserved.signum() < 0) {
            throw OpenException.of(AccountErrorCode.INSUFFICIENT_RESERVED_BALANCE,
                                   Map.of("userId", userId, "reserved", reserved, "required", totalReserved));
        }
        BigDecimal newBalance = balance.subtract(totalReserved).add(feeRefund);;
        if (newBalance.signum() < 0) {
            throw OpenException.of(AccountErrorCode.INSUFFICIENT_FUNDS,
                                   Map.of("userId", userId, "balance", balance, "required", totalReserved));
        }
        BigDecimal newAvailable = available.add(feeRefund);
        if (newAvailable.signum() < 0) {
            throw OpenException.of(AccountErrorCode.INSUFFICIENT_FUNDS,
                                   Map.of("userId", userId, "available", available, "required", 0));
        }
        return UserAccount.builder()
                          .accountId(accountId)
                          .userId(userId)
                          .accountCode(accountCode)
                          .accountName(accountName)
                          .instrumentId(instrumentId)
                          .category(category)
                          .asset(asset)
                          .balance(newBalance)
                          .available(newAvailable)
                          .reserved(newReserved)
                          .version(safeVersion() + 1)
                          .createdAt(createdAt)
                          .build();
    }
    
    public boolean hasEnoughAvailable(BigDecimal amount) {
        return available.compareTo(amount) >= 0;
    }

    public record MutationResult(UserAccount account, UserJournal journal) {
    }

    public MutationResult applyWithJournal(Direction direction,
                                           BigDecimal amount,
                                           Long journalId,
                                           ReferenceType referenceType,
                                           String referenceId,
                                           Integer seq,
                                           String description,
                                           Instant eventTime) {
        UserAccount updated = apply(direction, amount);
        UserJournal journal = buildJournal(updated, direction, amount, journalId, referenceType, referenceId, seq, description, eventTime);
        return new MutationResult(updated, journal);
    }

    public MutationResult applyAllowNegativeWithJournal(Direction direction,
                                                        BigDecimal amount,
                                                        Long journalId,
                                                        ReferenceType referenceType,
                                                        String referenceId,
                                                        Integer seq,
                                                        String description,
                                                        Instant eventTime) {
        UserAccount updated = applyAllowNegative(direction, amount);
        UserJournal journal = buildJournal(updated, direction, amount, journalId, referenceType, referenceId, seq, description, eventTime);
        return new MutationResult(updated, journal);
    }

    public MutationResult freezeWithJournal(BigDecimal amount,
                                            Long journalId,
                                            ReferenceType referenceType,
                                            String referenceId,
                                            Integer seq,
                                            String description,
                                            Instant eventTime) {
        UserAccount updated = freeze(amount);
        // User instruction: Freeze is decreasing (Available), should be CREDIT.
        UserJournal journal = buildJournal(updated, Direction.CREDIT, amount, journalId, referenceType, referenceId, seq, description, eventTime);
        return new MutationResult(updated, journal);
    }

    public UserAccount freeze(BigDecimal amount) {
        if (available.compareTo(amount) < 0) {
            throw OpenException.of(AccountErrorCode.INSUFFICIENT_FUNDS,
                                   Map.of("userId", userId, "asset", asset, "available", available, "amount", amount));
        }
        return UserAccount.builder()
                          .accountId(accountId)
                          .userId(userId)
                          .accountCode(accountCode)
                          .accountName(accountName)
                          .instrumentId(instrumentId)
                          .category(category)
                          .asset(asset)
                          .balance(balance)
                          .available(available.subtract(amount))
                          .reserved(reserved.add(amount))
                          .version(safeVersion() + 1)
                          .createdAt(createdAt)
                          .updatedAt(Instant.now())
                          .build();
    }

    public MutationResult applyReservedOutWithJournal(BigDecimal amount,
                                                      Long journalId,
                                                      ReferenceType referenceType,
                                                      String referenceId,
                                                      Integer seq,
                                                      String description,
                                                      Instant eventTime) {
        UserAccount updated = applyReservedOut(amount);
        // User instruction: Unfreeze/Usage from Reserved should be logged as DEBIT (likely tracking activity or specific view).
        UserJournal journal = buildJournal(updated, Direction.DEBIT, amount, journalId, referenceType, referenceId, seq, description, eventTime);
        return new MutationResult(updated, journal);
    }

    public UserAccount applyReservedOut(BigDecimal amount) {
        if (amount.signum() == 0) {
            return this;
        }
        if (reserved.compareTo(amount) < 0) {
            throw OpenException.of(AccountErrorCode.INSUFFICIENT_RESERVED_BALANCE,
                                   Map.of("userId", userId, "reserved", reserved, "required", amount));
        }
        BigDecimal newBalance = balance.subtract(amount);
        if (newBalance.signum() < 0) {
            throw OpenException.of(AccountErrorCode.INSUFFICIENT_FUNDS,
                                   Map.of("userId", userId, "balance", balance, "required", amount));
        }
        return UserAccount.builder()
                          .accountId(accountId)
                          .userId(userId)
                          .accountCode(accountCode)
                          .accountName(accountName)
                          .instrumentId(instrumentId)
                          .category(category)
                          .asset(asset)
                          .balance(newBalance)
                          .available(available)
                          .reserved(reserved.subtract(amount))
                          .version(safeVersion() + 1)
                          .createdAt(createdAt)
                          .updatedAt(Instant.now())
                          .build();
    }

    public MutationResult refundReservedWithJournal(BigDecimal amount,
                                                    Long journalId,
                                                    ReferenceType referenceType,
                                                    String referenceId,
                                                    Integer seq,
                                                    String description,
                                                    Instant eventTime) {
        UserAccount updated = refundReserved(amount);
        UserJournal journal = buildJournal(updated, Direction.DEBIT, amount, journalId, referenceType, referenceId, seq, description, eventTime);
        return new MutationResult(updated, journal);
    }

    public UserAccount refundReserved(BigDecimal amount) {
        if (amount.signum() == 0) {
            return this;
        }
        if (reserved.compareTo(amount) < 0) {
            throw OpenException.of(AccountErrorCode.INSUFFICIENT_RESERVED_BALANCE,
                                   Map.of("userId", userId, "reserved", reserved, "required", amount));
        }
        return UserAccount.builder()
                          .accountId(accountId)
                          .userId(userId)
                          .accountCode(accountCode)
                          .accountName(accountName)
                          .instrumentId(instrumentId)
                          .category(category)
                          .asset(asset)
                          .balance(balance)
                          .available(available.add(amount))
                          .reserved(reserved.subtract(amount))
                          .version(safeVersion() + 1)
                          .createdAt(createdAt)
                          .updatedAt(Instant.now())
                          .build();
    }

    private UserJournal buildJournal(UserAccount updatedAccount,
                                     Direction direction,
                                     BigDecimal amount,
                                     Long journalId,
                                     ReferenceType referenceType,
                                     String referenceId,
                                     Integer seq,
                                     String description,
                                     Instant eventTime) {
        return UserJournal.builder()
                .journalId(journalId)
                .userId(this.userId)
                .accountId(this.accountId)
                .category(this.category)
                .asset(this.asset)
                .amount(amount)
                .direction(direction)
                .balanceAfter(updatedAccount.getBalance())
                .referenceType(referenceType)
                .referenceId(referenceId)
                .seq(seq)
                .description(description)
                .eventTime(eventTime)
                .build();
    }
}