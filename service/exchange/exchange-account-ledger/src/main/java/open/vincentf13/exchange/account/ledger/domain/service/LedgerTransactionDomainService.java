package open.vincentf13.exchange.account.ledger.domain.service;

import com.github.yitter.idgen.DefaultIdGenerator;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.ledger.infra.exception.FundsFreezeException;
import open.vincentf13.exchange.account.ledger.infra.exception.FundsFreezeFailureReason;
import open.vincentf13.exchange.account.ledger.domain.model.LedgerBalance;
import open.vincentf13.exchange.account.ledger.domain.model.LedgerEntry;
import open.vincentf13.exchange.account.ledger.domain.model.PlatformAccount;
import open.vincentf13.exchange.account.ledger.domain.model.PlatformBalance;
import open.vincentf13.exchange.account.ledger.domain.model.transaction.LedgerDepositResult;
import open.vincentf13.exchange.account.ledger.domain.model.transaction.LedgerWithdrawalResult;
import open.vincentf13.exchange.account.ledger.infra.persistence.repository.LedgerBalanceRepository;
import open.vincentf13.exchange.account.ledger.infra.persistence.repository.LedgerEntryRepository;
import open.vincentf13.exchange.account.ledger.infra.persistence.repository.PlatformAccountRepository;
import open.vincentf13.exchange.account.ledger.infra.persistence.repository.PlatformBalanceRepository;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.dto.LedgerDepositRequest;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.dto.LedgerWithdrawalRequest;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.enums.AccountType;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.enums.EntryType;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.enums.PlatformAccountCode;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.enums.ReferenceType;
import open.vincentf13.exchange.sdk.common.enums.AssetSymbol;
import jakarta.validation.Valid;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

@Service
@Validated
@RequiredArgsConstructor
public class LedgerTransactionDomainService {

    private static final int OPTIMISTIC_LOCK_MAX_RETRIES = 3;

    private final LedgerBalanceRepository ledgerBalanceRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final PlatformAccountRepository platformAccountRepository;
    private final PlatformBalanceRepository platformBalanceRepository;
    private final DefaultIdGenerator idGenerator;

    public LedgerDepositResult deposit(@NotNull @Valid LedgerDepositRequest request) {
        AccountType accountType = AccountType.SPOT_MAIN;
        AssetSymbol normalizedAsset = LedgerBalance.normalizeAsset(request.asset());
        LedgerBalance userBalance = ledgerBalanceRepository.getOrCreate(request.userId(), accountType, null, normalizedAsset);
        LedgerBalance balanceUpdated = retryUpdateForDeposit(userBalance, request.amount(), request.userId(), normalizedAsset);

        PlatformAccount platformAccount = platformAccountRepository.getOrCreate(PlatformAccountCode.USER_DEPOSIT);
        PlatformBalance platformBalance = platformBalanceRepository.getOrCreate(platformAccount.getAccountId(), platformAccount.getAccountCode(), normalizedAsset);
        PlatformBalance platformBalanceUpdated = retryUpdateForPlatformDeposit(platformBalance, request.amount(), normalizedAsset);

        Instant createdAt = Instant.now();
        Long userEntryId = idGenerator.newLong();
        Long platformEntryId = idGenerator.newLong();

        LedgerEntry userEntry = LedgerEntry.userDeposit(
                userEntryId,
                balanceUpdated.getAccountId(),
                balanceUpdated.getUserId(),
                balanceUpdated.getAsset(),
                request.amount(),
                platformEntryId,
                balanceUpdated.getAvailable(),
                request.txId(),
                request.creditedAt(),
                createdAt
                                                       );
        ledgerEntryRepository.insert(userEntry);

        LedgerEntry platformEntry = LedgerEntry.platformDeposit(
                platformEntryId,
                platformBalanceUpdated.getAccountId(),
                platformBalanceUpdated.getAsset(),
                request.amount(),
                userEntryId,
                platformBalanceUpdated.getBalance(),
                request.txId(),
                request.creditedAt(),
                createdAt
                                                               );
        ledgerEntryRepository.insert(platformEntry);

        return new LedgerDepositResult(userEntry, platformEntry, balanceUpdated, platformBalanceUpdated);
    }

    public LedgerWithdrawalResult withdraw(@NotNull @Valid LedgerWithdrawalRequest request) {
        AccountType accountType = AccountType.SPOT_MAIN;
        AssetSymbol normalizedAsset = LedgerBalance.normalizeAsset(request.asset());
        LedgerBalance userBalance = ledgerBalanceRepository.getOrCreate(request.userId(), accountType, null, normalizedAsset);

        LedgerBalance balanceUpdated = retryUpdateForWithdrawal(userBalance, request.amount(), request.userId(), normalizedAsset);

        PlatformAccount platformAccount = platformAccountRepository.getOrCreate(PlatformAccountCode.USER_DEPOSIT);
        PlatformBalance platformBalance = platformBalanceRepository.getOrCreate(platformAccount.getAccountId(), platformAccount.getAccountCode(), normalizedAsset);
        PlatformBalance platformBalanceUpdated = retryUpdateForPlatformWithdrawal(platformBalance, request.amount(), normalizedAsset);

        Instant createdAt = Instant.now();
        Long userEntryId = idGenerator.newLong();
        Long platformEntryId = idGenerator.newLong();

        LedgerEntry userEntry = LedgerEntry.userWithdrawal(
                userEntryId,
                balanceUpdated.getAccountId(),
                balanceUpdated.getUserId(),
                balanceUpdated.getAsset(),
                request.amount(),
                balanceUpdated.getAvailable(),
                request.txId(),
                null,
                request.creditedAt(),
                createdAt
                                                          );
        ledgerEntryRepository.insert(userEntry);

        LedgerEntry platformEntry = LedgerEntry.platformWithdrawal(
                platformEntryId,
                platformBalanceUpdated.getAccountId(),
                platformBalanceUpdated.getAsset(),
                request.amount(),
                userEntryId,
                platformBalanceUpdated.getBalance(),
                request.txId(),
                request.creditedAt(),
                createdAt
                                                                  );
        ledgerEntryRepository.insert(platformEntry);

        return new LedgerWithdrawalResult(userEntry, balanceUpdated);
    }

    public LedgerEntry freezeForOrder(@NotNull Long orderId,
                                      @NotNull Long userId,
                                      @NotNull AssetSymbol asset,
                                      @NotNull @DecimalMin(value = "0.00000000") BigDecimal requiredMargin,
                                      Instant eventTime) {
        if (orderId == null || userId == null) {
            throw new FundsFreezeException(FundsFreezeFailureReason.INVALID_EVENT, "orderId and userId are required");
        }
        if (requiredMargin == null || requiredMargin.signum() < 0) {
            throw new FundsFreezeException(FundsFreezeFailureReason.INVALID_AMOUNT, "Required margin must not be negative");
        }
        Instant entryEventTime = eventTime == null ? Instant.now() : eventTime;
        AssetSymbol normalizedAsset = LedgerBalance.normalizeAsset(asset);
        String referenceId = orderId.toString();
        Optional<LedgerEntry> existing = ledgerEntryRepository.findByReference(ReferenceType.ORDER, referenceId, EntryType.FREEZE);
        if (existing.isPresent()) {
            return existing.get();
        }
        LedgerBalance userBalance = ledgerBalanceRepository.getOrCreate(userId, AccountType.SPOT_MAIN, null, normalizedAsset);
        LedgerBalance updatedBalance = retryUpdateForFreeze(userBalance, requiredMargin, userId, normalizedAsset, orderId);
        Instant now = Instant.now();
        Long freezeEntryId = idGenerator.newLong();
        Long reservedEntryId = idGenerator.newLong();
        LedgerEntry freezeEntry = LedgerEntry.userFundsFreeze(freezeEntryId,
                                                              updatedBalance.getAccountId(),
                                                              userId,
                                                              normalizedAsset,
                                                              requiredMargin,
                                                              updatedBalance.getAvailable(),
                                                              orderId,
                                                              reservedEntryId,
                                                              entryEventTime,
                                                              now);
        LedgerEntry reservedEntry = LedgerEntry.userFundsReserved(reservedEntryId,
                                                                  updatedBalance.getAccountId(),
                                                                  userId,
                                                                  normalizedAsset,
                                                                  requiredMargin,
                                                                  updatedBalance.getReserved(),
                                                                  orderId,
                                                                  freezeEntryId,
                                                                  entryEventTime,
                                                                  now);
        ledgerEntryRepository.insert(freezeEntry);
        ledgerEntryRepository.insert(reservedEntry);
        return freezeEntry;
    }

    public LedgerEntry settleTrade(@NotNull Long tradeId,
                                   @NotNull Long orderId,
                                   @NotNull Long instrumentId,
                                   @NotNull AssetSymbol asset,
                                   @NotNull @DecimalMin(value = "0.00000000") BigDecimal totalCost,
                                   @NotNull Instant eventTime) {
        BigDecimal normalizedCost = totalCost;
        Optional<LedgerEntry> existing = ledgerEntryRepository.findByReference(ReferenceType.TRADE,
                                                                               tradeId.toString(),
                                                                               EntryType.TRADE);
        if (existing.isPresent()) {
            return existing.get();
        }
        LedgerEntry reservedEntry = ledgerEntryRepository.findByReference(ReferenceType.ORDER,
                                                                          orderId.toString(),
                                                                          EntryType.RESERVED)
                .orElseThrow(() -> new IllegalStateException("Reserved entry not found for order=" + orderId));
        AssetSymbol normalizedAsset = LedgerBalance.normalizeAsset(asset);
        if (reservedEntry.getAsset() != null && normalizedAsset != null && reservedEntry.getAsset() != normalizedAsset) {
            throw new IllegalStateException("Asset mismatch for order=" + orderId + ", reserved=" + reservedEntry.getAsset()
                    + ", event=" + normalizedAsset);
        }
        LedgerBalance balance = ledgerBalanceRepository.findOne(LedgerBalance.builder()
                                                                     .accountId(reservedEntry.getAccountId())
                                                                     .asset(normalizedAsset)
                                                                     .build())
                .orElseThrow(() -> new IllegalStateException("Ledger balance not found for account=" + reservedEntry.getAccountId()));
        LedgerBalance updated = retryUpdateForTrade(balance,
                                                    normalizedCost,
                                                    reservedEntry.getUserId(),
                                                    normalizedAsset,
                                                    tradeId);
        Instant entryEventTime = eventTime == null ? Instant.now() : eventTime;
        Instant createdAt = Instant.now();
        Long tradeEntryId = idGenerator.newLong();
        LedgerEntry tradeEntry = LedgerEntry.tradeSettlement(tradeEntryId,
                                                             updated.getAccountId(),
                                                             updated.getUserId(),
                                                             normalizedAsset,
                                                             normalizedCost,
                                                             updated.getBalance(),
                                                             tradeId,
                                                             orderId,
                                                             instrumentId,
                                                             entryEventTime,
                                                             createdAt);
        ledgerEntryRepository.insert(tradeEntry);
        return tradeEntry;
    }

    private LedgerBalance retryUpdateForFreeze(LedgerBalance balance,
                                               BigDecimal amount,
                                               Long userId,
                                               AssetSymbol asset,
                                               Long orderId) {
        LedgerBalance current = balance;
        int attempts = 0;
        while (attempts < OPTIMISTIC_LOCK_MAX_RETRIES) {
            int expectedVersion = current.safeVersion();
            BigDecimal available = safeDecimal(current.getAvailable());
            if (available.compareTo(amount) < 0) {
                throw new FundsFreezeException(FundsFreezeFailureReason.INSUFFICIENT_FUNDS,
                                               "Insufficient available balance for user=" + userId + " asset=" + asset.code());
            }
            BigDecimal reserved = safeDecimal(current.getReserved());
            current.setAvailable(available.subtract(amount));
            current.setReserved(reserved.add(amount));
            current.setVersion(expectedVersion + 1);
            boolean updated = ledgerBalanceRepository.updateWithVersion(current, expectedVersion);
            if (updated) {
                return current;
            }
            attempts++;
            current = reloadLedgerBalance(current.getUserId(), current.getAccountType(), current.getInstrumentId(), asset);
        }
        throw new OptimisticLockingFailureException(
                "Failed to freeze funds for user=" + userId + " order=" + orderId);
    }

    private LedgerBalance retryUpdateForTrade(LedgerBalance balance,
                                              BigDecimal amount,
                                              Long userId,
                                              AssetSymbol asset,
                                              Long tradeId) {
        LedgerBalance current = balance;
        int attempts = 0;
        while (attempts < OPTIMISTIC_LOCK_MAX_RETRIES) {
            int expectedVersion = current.safeVersion();
            BigDecimal reserved = safeDecimal(current.getReserved());
            if (reserved.compareTo(amount) < 0) {
                throw new IllegalStateException("Insufficient reserved balance for user=" + userId + ", trade=" + tradeId);
            }
            BigDecimal totalBalance = safeDecimal(current.getBalance());
            current.setReserved(reserved.subtract(amount));
            current.setBalance(totalBalance.subtract(amount));
            current.setVersion(expectedVersion + 1);
            boolean updated = ledgerBalanceRepository.updateWithVersion(current, expectedVersion);
            if (updated) {
                return current;
            }
            attempts++;
            current = reloadLedgerBalance(current.getAccountId(), asset);
        }
        throw new OptimisticLockingFailureException("Failed to settle trade for user=" + userId + ", trade=" + tradeId);
    }

    private LedgerBalance retryUpdateForDeposit(LedgerBalance balance,
                                                BigDecimal amount,
                                                Long userId,
                                                AssetSymbol asset) {
        LedgerBalance current = balance;
        int attempts = 0;
        while (attempts < OPTIMISTIC_LOCK_MAX_RETRIES) {
            int expectedVersion = current.safeVersion();
            current.setBalance(current.getBalance().add(amount));
            current.setAvailable(current.getAvailable().add(amount));
            current.setTotalDeposited(current.getTotalDeposited().add(amount));
            current.setVersion(expectedVersion + 1);
            boolean updated = ledgerBalanceRepository.updateWithVersion(current, expectedVersion);
            if (updated) {
                return current;
            }
            attempts++;
            current = reloadLedgerBalance(current.getUserId(), current.getAccountType(), current.getInstrumentId(), asset);
        }
        throw new OptimisticLockingFailureException("Failed to update ledger balance for user=" + userId);
    }

    private LedgerBalance retryUpdateForWithdrawal(LedgerBalance balance,
                                                   BigDecimal amount,
                                                   Long userId,
                                                   AssetSymbol asset) {
        LedgerBalance current = balance;
        int attempts = 0;
        while (attempts < OPTIMISTIC_LOCK_MAX_RETRIES) {
            int expectedVersion = current.safeVersion();
            BigDecimal available = safeDecimal(current.getAvailable());
            if (available.compareTo(amount) < 0) {
                throw new FundsFreezeException(FundsFreezeFailureReason.INSUFFICIENT_FUNDS,
                        "Insufficient available balance for user=" + userId + " asset=" + asset.code());
            }
            current.setAvailable(available.subtract(amount));
            current.setBalance(current.getBalance().subtract(amount));
            current.setTotalWithdrawn(current.getTotalWithdrawn().add(amount));
            current.setVersion(expectedVersion + 1);
            boolean updated = ledgerBalanceRepository.updateWithVersion(current, expectedVersion);
            if (updated) {
                return current;
            }
            attempts++;
            current = reloadLedgerBalance(current.getUserId(), current.getAccountType(), current.getInstrumentId(), asset);
        }
        throw new OptimisticLockingFailureException("Failed to update ledger balance for user=" + userId);
    }

    private LedgerBalance reloadLedgerBalance(Long userId,
                                              AccountType accountType,
                                              Long instrumentId,
                                              AssetSymbol asset) {
        return ledgerBalanceRepository.findOne(LedgerBalance.builder()
                                                       .userId(userId)
                                                       .accountType(accountType)
                                                       .instrumentId(instrumentId)
                                                       .asset(asset)
                                                       .build())
                .orElseThrow(() -> new IllegalStateException(
                        "Ledger balance not found for user=" + userId + ", asset=" + asset.code()));
    }

    private LedgerBalance reloadLedgerBalance(Long accountId, AssetSymbol asset) {
        return ledgerBalanceRepository.findOne(LedgerBalance.builder()
                                                       .accountId(accountId)
                                                       .asset(asset)
                                                       .build())
                .orElseThrow(() -> new IllegalStateException(
                        "Ledger balance not found for account=" + accountId + ", asset=" + asset.code()));
    }

    private BigDecimal safeDecimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private PlatformBalance retryUpdateForPlatformDeposit(PlatformBalance platformBalance,
                                                          BigDecimal amount,
                                                          AssetSymbol asset) {
        PlatformBalance current = platformBalance;
        int attempts = 0;
        while (attempts < OPTIMISTIC_LOCK_MAX_RETRIES) {
            int expectedVersion = current.safeVersion();
            BigDecimal currentBalance = safeDecimal(current.getBalance());
            current.setBalance(currentBalance.add(amount));
            current.setVersion(expectedVersion + 1);
            boolean updated = platformBalanceRepository.updateWithVersion(current, expectedVersion);
            if (updated) {
                return current;
            }
            attempts++;
            current = reloadPlatformBalance(current.getAccountId(), current.getAccountCode(), asset);
        }
        throw new OptimisticLockingFailureException("Failed to update platform balance for account=" + platformBalance.getAccountId());
    }

    private PlatformBalance retryUpdateForPlatformWithdrawal(PlatformBalance platformBalance,
                                                             BigDecimal amount,
                                                             AssetSymbol asset) {
        PlatformBalance current = platformBalance;
        int attempts = 0;
        while (attempts < OPTIMISTIC_LOCK_MAX_RETRIES) {
            int expectedVersion = current.safeVersion();
            BigDecimal currentBalance = safeDecimal(current.getBalance());
            current.setBalance(currentBalance.subtract(amount));
            current.setVersion(expectedVersion + 1);
            boolean updated = platformBalanceRepository.updateWithVersion(current, expectedVersion);
            if (updated) {
                return current;
            }
            attempts++;
            current = reloadPlatformBalance(current.getAccountId(), current.getAccountCode(), asset);
        }
        throw new OptimisticLockingFailureException("Failed to update platform balance for account=" + platformBalance.getAccountId());
    }

    private PlatformBalance reloadPlatformBalance(Long accountId, PlatformAccountCode accountCode, AssetSymbol asset) {
        return platformBalanceRepository.findOne(PlatformBalance.builder()
                                                         .accountId(accountId)
                                                         .accountCode(accountCode)
                                                         .asset(asset)
                                                         .build())
                .orElseThrow(() -> new IllegalStateException("Platform balance not found for account=" + accountId + ", asset=" + asset.code()));
    }

}
