package open.vincentf13.exchange.account.ledger.domain.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.yitter.idgen.DefaultIdGenerator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.ledger.domain.model.LedgerBalance;
import open.vincentf13.exchange.account.ledger.domain.model.LedgerEntry;
import open.vincentf13.exchange.account.ledger.domain.model.PlatformAccount;
import open.vincentf13.exchange.account.ledger.domain.model.PlatformBalance;
import open.vincentf13.exchange.account.ledger.domain.model.transaction.LedgerDepositResult;
import open.vincentf13.exchange.account.ledger.domain.model.transaction.LedgerWithdrawalResult;
import open.vincentf13.exchange.account.ledger.infra.exception.FundsFreezeException;
import open.vincentf13.exchange.account.ledger.infra.exception.FundsFreezeFailureReason;
import open.vincentf13.exchange.account.ledger.infra.persistence.po.LedgerBalancePO;
import open.vincentf13.exchange.account.ledger.infra.persistence.po.LedgerEntryPO;
import open.vincentf13.exchange.account.ledger.infra.persistence.po.PlatformBalancePO;
import open.vincentf13.exchange.account.ledger.infra.persistence.repository.LedgerBalanceRepository;
import open.vincentf13.exchange.account.ledger.infra.persistence.repository.LedgerEntryRepository;
import open.vincentf13.exchange.account.ledger.infra.persistence.repository.PlatformAccountRepository;
import open.vincentf13.exchange.account.ledger.infra.persistence.repository.PlatformBalanceRepository;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.dto.LedgerDepositRequest;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.dto.LedgerWithdrawalRequest;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.enums.*;
import open.vincentf13.exchange.common.sdk.constants.ValidationConstant;
import open.vincentf13.exchange.common.sdk.enums.AssetSymbol;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

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

        PlatformAccount platformAccount = platformAccountRepository.getOrCreate(
                PlatformAccountCode.USER_DEPOSIT,
                PlatformAccountCategory.LIABILITY,
                PlatformAccountStatus.ACTIVE);
        PlatformBalance platformBalance = platformBalanceRepository.getOrCreate(platformAccount.getAccountId(), platformAccount.getAccountCode(), normalizedAsset);
        PlatformBalance platformBalanceUpdated = retryUpdateForPlatformDeposit(platformBalance, request.amount(), normalizedAsset);

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
                request.creditedAt()
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
                request.creditedAt()
                                                               );
        ledgerEntryRepository.insert(platformEntry);

        return new LedgerDepositResult(userEntry, platformEntry, balanceUpdated, platformBalanceUpdated);
    }

    public LedgerWithdrawalResult withdraw(@NotNull @Valid LedgerWithdrawalRequest request) {
        AccountType accountType = AccountType.SPOT_MAIN;
        AssetSymbol normalizedAsset = LedgerBalance.normalizeAsset(request.asset());
        LedgerBalance userBalance = ledgerBalanceRepository.getOrCreate(request.userId(), accountType, null, normalizedAsset);

        LedgerBalance balanceUpdated = retryUpdateForWithdrawal(userBalance, request.amount(), request.userId(), normalizedAsset);

        PlatformAccount platformAccount = platformAccountRepository.getOrCreate(
                PlatformAccountCode.USER_DEPOSIT,
                PlatformAccountCategory.LIABILITY,
                PlatformAccountStatus.ACTIVE);
        PlatformBalance platformBalance = platformBalanceRepository.getOrCreate(platformAccount.getAccountId(), platformAccount.getAccountCode(), normalizedAsset);
        PlatformBalance platformBalanceUpdated = retryUpdateForPlatformWithdrawal(platformBalance, request.amount(), normalizedAsset);

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
                request.creditedAt()
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
                request.creditedAt()
                                                                );
        ledgerEntryRepository.insert(platformEntry);

        return new LedgerWithdrawalResult(userEntry, balanceUpdated);
    }

    public LedgerEntry freezeForOrder(@NotNull Long orderId,
                                      @NotNull Long userId,
                                      @NotNull AssetSymbol asset,
                                       @NotNull @DecimalMin(value = ValidationConstant.Names.AMOUNT_MIN) BigDecimal requiredMargin,
                                      Instant eventTime) {
        Instant entryEventTime = eventTime == null ? Instant.now() : eventTime;
        AssetSymbol normalizedAsset = LedgerBalance.normalizeAsset(asset);
        String referenceId = orderId.toString();
        Optional<LedgerEntry> existing = ledgerEntryRepository.findOne(
                Wrappers.lambdaQuery(LedgerEntryPO.class)
                        .eq(LedgerEntryPO::getReferenceType, ReferenceType.ORDER)
                        .eq(LedgerEntryPO::getReferenceId, referenceId)
                        .eq(LedgerEntryPO::getEntryType, EntryType.FREEZE));
        if (existing.isPresent()) {
            return existing.get();
        }
        LedgerBalance userBalance = ledgerBalanceRepository.getOrCreate(userId, AccountType.SPOT_MAIN, null, normalizedAsset);
        LedgerBalance updatedBalance = retryUpdateForFreeze(userBalance, requiredMargin, userId, normalizedAsset, orderId);
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
                                                              entryEventTime);
        LedgerEntry reservedEntry = LedgerEntry.userFundsReserved(reservedEntryId,
                                                                  updatedBalance.getAccountId(),
                                                                  userId,
                                                                  normalizedAsset,
                                                                  requiredMargin,
                                                                  updatedBalance.getReserved(),
                                                                  orderId,
                                                                  freezeEntryId,
                                                                  entryEventTime);
        ledgerEntryRepository.insert(freezeEntry);
        ledgerEntryRepository.insert(reservedEntry);
        return freezeEntry;
    }

    public LedgerEntry settleTrade(@NotNull Long tradeId,
                                   @NotNull Long instrumentId, @NotNull AssetSymbol asset, @NotNull Long takerOrderId, @NotNull Long makerOrderId,
                                   @NotNull @DecimalMin(value = ValidationConstant.Names.PRICE_MIN) BigDecimal price,
                                   @NotNull @DecimalMin(value = ValidationConstant.Names.QUANTITY_MIN) BigDecimal quantity,
                                   @NotNull @DecimalMin(value = ValidationConstant.Names.FEE_MIN) BigDecimal makerFee,
                                   @NotNull @DecimalMin(value = ValidationConstant.Names.FEE_MIN) BigDecimal takerFee,
                                   @NotNull Long makerUserId,
                                   @NotNull Long takerUserId,
                                   @NotNull Instant eventTime) {
        BigDecimal totalCost = price.multiply(quantity).add(takerFee);
        Optional<LedgerEntry> existing = ledgerEntryRepository.findOne(
                Wrappers.lambdaQuery(LedgerEntryPO.class)
                        .eq(LedgerEntryPO::getReferenceType, ReferenceType.TRADE)
                        .eq(LedgerEntryPO::getReferenceId, tradeId.toString())
                        .eq(LedgerEntryPO::getEntryType, EntryType.TRADE));
        if (existing.isPresent()) {
            return existing.get();
        }
        LedgerEntry reservedEntry = ledgerEntryRepository.findOne(
                        Wrappers.lambdaQuery(LedgerEntryPO.class)
                                .eq(LedgerEntryPO::getReferenceType, ReferenceType.ORDER)
                                .eq(LedgerEntryPO::getReferenceId, takerOrderId.toString())
                                .eq(LedgerEntryPO::getEntryType, EntryType.RESERVED))
                .orElseThrow(() -> new IllegalStateException(
                        "Reserved entry not found for takerOrderId=" + takerOrderId + ", makerOrderId=" + makerOrderId));
        AssetSymbol normalizedAsset = LedgerBalance.normalizeAsset(asset);
        if (reservedEntry.getAsset() != null && normalizedAsset != null && reservedEntry.getAsset() != normalizedAsset) {
            throw new IllegalStateException("Asset mismatch for order=" + orderId + ", reserved=" + reservedEntry.getAsset()
                    + ", event=" + normalizedAsset);
        }
        LedgerBalance balance = ledgerBalanceRepository.findOne(
                        Wrappers.lambdaQuery(LedgerBalancePO.class)
                                .eq(LedgerBalancePO::getAccountId, reservedEntry.getAccountId())
                                .eq(LedgerBalancePO::getAsset, normalizedAsset))
                .orElseThrow(() -> new IllegalStateException("Ledger balance not found for account=" + reservedEntry.getAccountId()));
        LedgerBalance updated = retryUpdateForTrade(balance,
                                                    totalCost,
                                                    reservedEntry.getUserId(),
                                                    normalizedAsset,
                                                    tradeId);
        Instant entryEventTime = eventTime == null ? Instant.now() : eventTime;
        Long tradeEntryId = idGenerator.newLong();
        LedgerEntry tradeEntry = LedgerEntry.tradeSettlement(tradeEntryId,
                                                             updated.getAccountId(),
                                                             updated.getUserId(),
                                                             normalizedAsset,
                                                             totalCost,
                                                             updated.getBalance(),
                                                             tradeId,
                                                             takerOrderId,
                                                             instrumentId,
                                                             entryEventTime);
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
            boolean updated = ledgerBalanceRepository.updateSelectiveBy(
                    current,
                    new LambdaUpdateWrapper<LedgerBalancePO>()
                            .eq(LedgerBalancePO::getId, current.getId())
                            .eq(LedgerBalancePO::getVersion, expectedVersion)
            );
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
            boolean updated = ledgerBalanceRepository.updateSelectiveBy(
                    current,
                    new LambdaUpdateWrapper<LedgerBalancePO>()
                            .eq(LedgerBalancePO::getId, current.getId())
                            .eq(LedgerBalancePO::getVersion, expectedVersion)
            );
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
            boolean updated = ledgerBalanceRepository.updateSelectiveBy(
                    current,
                    new LambdaUpdateWrapper<LedgerBalancePO>()
                            .eq(LedgerBalancePO::getId, current.getId())
                            .eq(LedgerBalancePO::getVersion, expectedVersion)
            );
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
            boolean updated = ledgerBalanceRepository.updateSelectiveBy(
                    current,
                    new LambdaUpdateWrapper<LedgerBalancePO>()
                            .eq(LedgerBalancePO::getId, current.getId())
                            .eq(LedgerBalancePO::getVersion, expectedVersion)
            );
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
        return ledgerBalanceRepository.findOne(
                        Wrappers.lambdaQuery(LedgerBalancePO.class)
                                .eq(LedgerBalancePO::getUserId, userId)
                                .eq(LedgerBalancePO::getAccountType, accountType)
                                .eq(LedgerBalancePO::getInstrumentId, instrumentId)
                                .eq(LedgerBalancePO::getAsset, asset))
                .orElseThrow(() -> new IllegalStateException(
                        "Ledger balance not found for user=" + userId + ", asset=" + asset.code()));
    }

    private LedgerBalance reloadLedgerBalance(Long accountId, AssetSymbol asset) {
        return ledgerBalanceRepository.findOne(
                        Wrappers.lambdaQuery(LedgerBalancePO.class)
                                .eq(LedgerBalancePO::getAccountId, accountId)
                                .eq(LedgerBalancePO::getAsset, asset))
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
            boolean updated = platformBalanceRepository.updateSelectiveBy(
                    current,
                    new LambdaUpdateWrapper<PlatformBalancePO>()
                            .eq(PlatformBalancePO::getId, current.getId())
                            .eq(PlatformBalancePO::getVersion, expectedVersion)
            );
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
            boolean updated = platformBalanceRepository.updateSelectiveBy(
                    current,
                    new LambdaUpdateWrapper<PlatformBalancePO>()
                            .eq(PlatformBalancePO::getId, current.getId())
                            .eq(PlatformBalancePO::getVersion, expectedVersion)
            );
            if (updated) {
                return current;
            }
            attempts++;
            current = reloadPlatformBalance(current.getAccountId(), current.getAccountCode(), asset);
        }
        throw new OptimisticLockingFailureException("Failed to update platform balance for account=" + platformBalance.getAccountId());
    }

    private PlatformBalance reloadPlatformBalance(Long accountId, PlatformAccountCode accountCode, AssetSymbol asset) {
        return platformBalanceRepository.findOne(
                        Wrappers.lambdaQuery(PlatformBalancePO.class)
                                .eq(PlatformBalancePO::getAccountId, accountId)
                                .eq(PlatformBalancePO::getAccountCode, accountCode)
                                .eq(PlatformBalancePO::getAsset, asset))
                .orElseThrow(() -> new IllegalStateException("Platform balance not found for account=" + accountId + ", asset=" + asset.code()));
    }

}
