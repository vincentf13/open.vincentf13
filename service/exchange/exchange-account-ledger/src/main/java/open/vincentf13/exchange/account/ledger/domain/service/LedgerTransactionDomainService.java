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
import open.vincentf13.exchange.account.ledger.infra.messaging.publisher.LedgerEventPublisher;
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
import open.vincentf13.exchange.matching.sdk.mq.event.TradeExecutedEvent;
import open.vincentf13.exchange.order.sdk.rest.dto.OrderResponse;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
    private final LedgerEventPublisher ledgerEventPublisher;

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

    @Transactional
    public void settleTrade(@NotNull @Valid TradeExecutedEvent event, @NotNull @Valid OrderResponse order, boolean isMaker) {
        AssetSymbol normalizedAsset = LedgerBalance.normalizeAsset(event.quoteAsset());
        String referenceId = event.tradeId() + ":" + (isMaker ? "maker" : "taker");

        Optional<LedgerEntry> existing = ledgerEntryRepository.findOne(
                Wrappers.lambdaQuery(LedgerEntryPO.class)
                        .eq(LedgerEntryPO::getReferenceType, ReferenceType.TRADE)
                        .eq(LedgerEntryPO::getReferenceId, referenceId));
        if (existing.isPresent()) {
            return;
        }

        if (order.intent() == null) {
            throw new IllegalStateException("Order intent is null for orderId=" + order.orderId());
        }

        switch (order.intent()) {
            case INCREASE:
                processOpenPosition(event, order, isMaker, normalizedAsset, referenceId);
                break;
            case REDUCE:
            case CLOSE:
                processClosePosition(event, order, isMaker, normalizedAsset, referenceId);
                break;
            default:
                throw new IllegalStateException("Unsupported order intent: " + order.intent());
        }
    }

    private void processOpenPosition(TradeExecutedEvent event, OrderResponse order, boolean isMaker, AssetSymbol normalizedAsset, String referenceId) {
        BigDecimal fee = isMaker ? event.makerFee() : event.takerFee();
        BigDecimal tradeValue = event.price().multiply(event.quantity());
        Long userId = isMaker ? event.makerUserId() : event.takerUserId();

        // 1. Update SPOT_MAIN: Unreserve and decrease balance (for trade value and fee)
        LedgerBalance spotBalance = ledgerBalanceRepository.getOrCreate(userId, AccountType.SPOT_MAIN, null, normalizedAsset);
        LedgerBalance updatedSpotBalance = unfreezeAndDebitSpotBalance(spotBalance, tradeValue.add(fee), userId, normalizedAsset, event.tradeId());

        // 2. Update ISOLATED_MARGIN: Increase balance with trade value
        LedgerBalance marginBalance = ledgerBalanceRepository.getOrCreate(userId, AccountType.ISOLATED_MARGIN, event.instrumentId(), normalizedAsset);
        LedgerBalance updatedMarginBalance = retryUpdateForDeposit(marginBalance, tradeValue, userId, normalizedAsset);

        // 3. Platform account for fee revenue
        PlatformAccount feeRevenueAccount = platformAccountRepository.getOrCreate(PlatformAccountCode.FEE_REVENUE, PlatformAccountCategory.REVENUE, PlatformAccountStatus.ACTIVE);
        PlatformBalance feeRevenueBalance = platformBalanceRepository.getOrCreate(feeRevenueAccount.getAccountId(), feeRevenueAccount.getAccountCode(), normalizedAsset);
        PlatformBalance updatedFeeRevenueBalance = retryUpdateForPlatformDeposit(feeRevenueBalance, fee, normalizedAsset);

        // 4. Record ledger entries for user spot balance (debit for unreserve, credit for fee)
        Long userSpotDebitEntryId = idGenerator.newLong();
        Long userSpotCreditEntryId = idGenerator.newLong();
        Long feeRevenueEntryId = idGenerator.newLong();
        Long userMarginCreditEntryId = idGenerator.newLong();

        // User SPOT_MAIN debit (unreserve trade value + fee)
        ledgerEntryRepository.insert(LedgerEntry.settlement(userSpotDebitEntryId, updatedSpotBalance.getAccountId(), userId, normalizedAsset, tradeValue.add(fee).negate(), userSpotCreditEntryId, updatedSpotBalance.getBalance(), referenceId, order.orderId(), event.instrumentId(), event.executedAt()));

        // User ISOLATED_MARGIN credit (trade value)
        ledgerEntryRepository.insert(LedgerEntry.settlement(userMarginCreditEntryId, updatedMarginBalance.getAccountId(), userId, normalizedAsset, tradeValue, userSpotDebitEntryId, updatedMarginBalance.getBalance(), referenceId, order.orderId(), event.instrumentId(), event.executedAt()));

        // Platform FEE_REVENUE credit (fee)
        ledgerEntryRepository.insert(LedgerEntry.settlement(feeRevenueEntryId, updatedFeeRevenueBalance.getAccountId(), null, normalizedAsset, fee, userSpotCreditEntryId, updatedFeeRevenueBalance.getBalance(), referenceId, order.orderId(), event.instrumentId(), event.executedAt()));

        // Publish LedgerEntryCreated events
        ledgerEventPublisher.publishLedgerEntryCreated(userSpotDebitEntryId, userId, normalizedAsset, tradeValue.negate(), BigDecimal.ZERO, updatedSpotBalance.getBalance(), ReferenceType.TRADE, referenceId, EntryType.TRADE_SETTLEMENT, event.instrumentId(), event.executedAt());
        ledgerEventPublisher.publishLedgerEntryCreated(userMarginCreditEntryId, userId, normalizedAsset, tradeValue, BigDecimal.ZERO, updatedMarginBalance.getBalance(), ReferenceType.TRADE, referenceId, EntryType.TRADE_SETTLEMENT, event.instrumentId(), event.executedAt());
        ledgerEventPublisher.publishLedgerEntryCreated(feeRevenueEntryId, null, normalizedAsset, fee, BigDecimal.ZERO, updatedFeeRevenueBalance.getBalance(), ReferenceType.TRADE, referenceId, EntryType.FEE, event.instrumentId(), event.executedAt());
    }

    private void processClosePosition(TradeExecutedEvent event, open.vincentf1e.exchange.order.sdk.rest.dto.OrderResponse order, boolean isMaker, AssetSymbol normalizedAsset, String referenceId) {
        BigDecimal fee = isMaker ? event.makerFee() : event.takerFee();
        Long userId = isMaker ? event.makerUserId() : event.takerUserId();
        BigDecimal realizedPnl = (event.price().subtract(order.closeCostPrice())).multiply(event.quantity());

        // 1. Update ISOLATED_MARGIN: Decrease balance (cost basis + realized PnL)
        LedgerBalance marginBalance = ledgerBalanceRepository.getOrCreate(userId, AccountType.ISOLATED_MARGIN, event.instrumentId(), normalizedAsset);
        BigDecimal costBasis = order.closeCostPrice().multiply(event.quantity());
        BigDecimal totalDecreaseFromMargin = costBasis.add(realizedPnl);
        LedgerBalance updatedMarginBalance = retryUpdateForWithdrawal(marginBalance, totalDecreaseFromMargin, userId, normalizedAsset);

        // 2. Update SPOT_MAIN: Increase available balance with proceeds (cost basis + realized PnL - fee)
        LedgerBalance spotBalance = ledgerBalanceRepository.getOrCreate(userId, AccountType.SPOT_MAIN, null, normalizedAsset);
        BigDecimal releaseAmountToSpot = totalDecreaseFromMargin.subtract(fee);
        LedgerBalance updatedSpotBalance = retryUpdateForDeposit(spotBalance, releaseAmountToSpot, userId, normalizedAsset);

        // 3. Platform account for fee revenue
        PlatformAccount feeRevenueAccount = platformAccountRepository.getOrCreate(PlatformAccountCode.FEE_REVENUE, PlatformAccountCategory.REVENUE, PlatformAccountStatus.ACTIVE);
        PlatformBalance feeRevenueBalance = platformBalanceRepository.getOrCreate(feeRevenueAccount.getAccountId(), feeRevenueAccount.getAccountCode(), normalizedAsset);
        PlatformBalance updatedFeeRevenueBalance = retryUpdateForPlatformDeposit(feeRevenueBalance, fee, normalizedAsset);

        // 4. Record ledger entries
        Long userMarginDebitEntryId = idGenerator.newLong();
        Long userSpotCreditEntryId = idGenerator.newLong();
        Long feeRevenueEntryId = idGenerator.newLong();

        // User ISOLATED_MARGIN debit (cost basis + realized PnL)
        ledgerEntryRepository.insert(LedgerEntry.settlement(userMarginDebitEntryId, updatedMarginBalance.getAccountId(), userId, normalizedAsset, totalDecreaseFromMargin.negate(), userSpotCreditEntryId, updatedMarginBalance.getBalance(), referenceId, order.orderId(), event.instrumentId(), event.executedAt()));

        // User SPOT_MAIN credit (proceeds)
        ledgerEntryRepository.insert(LedgerEntry.settlement(userSpotCreditEntryId, updatedSpotBalance.getAccountId(), userId, normalizedAsset, releaseAmountToSpot, userMarginDebitEntryId, updatedSpotBalance.getBalance(), referenceId, order.orderId(), event.instrumentId(), event.executedAt()));

        // Platform FEE_REVENUE credit (fee)
        ledgerEntryRepository.insert(LedgerEntry.settlement(feeRevenueEntryId, updatedFeeRevenueBalance.getAccountId(), null, normalizedAsset, fee, userSpotCreditEntryId, updatedFeeRevenueBalance.getBalance(), referenceId, order.orderId(), event.instrumentId(), event.executedAt()));

        // Publish LedgerEntryCreated events
        ledgerEventPublisher.publishLedgerEntryCreated(userMarginDebitEntryId, userId, normalizedAsset, totalDecreaseFromMargin.negate(), BigDecimal.ZERO, updatedMarginBalance.getBalance(), ReferenceType.TRADE, referenceId, EntryType.TRADE_SETTLEMENT, event.instrumentId(), event.executedAt());
        ledgerEventPublisher.publishLedgerEntryCreated(userSpotCreditEntryId, userId, normalizedAsset, releaseAmountToSpot, BigDecimal.ZERO, updatedSpotBalance.getBalance(), ReferenceType.TRADE, referenceId, EntryType.TRADE_SETTLEMENT, event.instrumentId(), event.executedAt());
        ledgerEventPublisher.publishLedgerEntryCreated(feeRevenueEntryId, null, normalizedAsset, fee, BigDecimal.ZERO, updatedFeeRevenueBalance.getBalance(), ReferenceType.TRADE, referenceId, EntryType.FEE, event.instrumentId(), event.executedAt());
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

    private LedgerBalance unfreezeAndDebitSpotBalance(LedgerBalance balance,
                                                      BigDecimal amount,
                                                      Long userId,
                                                      AssetSymbol asset,
                                                      Long tradeId) {
        LedgerBalance current = balance;
        int attempts = 0;
        while (attempts < OPTIMISTIC_LOCK_MAX_RETRIES) {
            int expectedVersion = current.safeVersion();
            BigDecimal available = safeDecimal(current.getAvailable());
            BigDecimal reserved = safeDecimal(current.getReserved());

            // Ensure there are enough reserved funds to unfreeze, and enough available funds after unfreezing to cover the debit
            if (reserved.compareTo(amount) < 0) {
                throw new IllegalStateException("Insufficient reserved balance for user=" + userId + ", trade=" + tradeId);
            }
            
            // Unfreeze the amount from reserved, and debit from available/balance
            current.setReserved(reserved.subtract(amount));
            current.setAvailable(available.subtract(amount)); // This should already be covered by the initial freeze
            current.setBalance(current.getBalance().subtract(amount));
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
        throw new OptimisticLockingFailureException("Failed to unfreeze and debit spot balance for user=" + userId + ", trade=" + tradeId);
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