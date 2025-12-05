package open.vincentf13.exchange.account.domain.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.yitter.idgen.DefaultIdGenerator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.domain.model.AccountBalance;
import open.vincentf13.exchange.account.domain.model.AccountEntry;
import open.vincentf13.exchange.account.domain.model.PlatformAccount;
import open.vincentf13.exchange.account.domain.model.PlatformBalance;
import open.vincentf13.exchange.account.domain.model.transaction.AccountDepositResult;
import open.vincentf13.exchange.account.domain.model.transaction.AccountWithdrawalResult;
import open.vincentf13.exchange.account.infra.AccountErrorCode;
import open.vincentf13.exchange.account.infra.messaging.publisher.AccountEventPublisher;
import open.vincentf13.exchange.account.infra.persistence.po.AccountBalancePO;
import open.vincentf13.exchange.account.infra.persistence.po.AccountEntryPO;
import open.vincentf13.exchange.account.infra.persistence.po.PlatformBalancePO;
import open.vincentf13.exchange.account.infra.persistence.repository.AccountBalanceRepository;
import open.vincentf13.exchange.account.infra.persistence.repository.AccountEntryRepository;
import open.vincentf13.exchange.account.infra.persistence.repository.PlatformAccountRepository;
import open.vincentf13.exchange.account.infra.persistence.repository.PlatformBalanceRepository;
import open.vincentf13.exchange.account.sdk.rest.api.dto.AccountDepositRequest;
import open.vincentf13.exchange.account.sdk.rest.api.dto.AccountWithdrawalRequest;
import open.vincentf13.exchange.common.sdk.enums.*;
import open.vincentf13.exchange.common.sdk.constants.ValidationConstant;
import open.vincentf13.exchange.common.sdk.enums.AssetSymbol;
import open.vincentf13.exchange.matching.sdk.mq.event.TradeExecutedEvent;
import open.vincentf13.exchange.order.sdk.rest.dto.OrderResponse;
import open.vincentf13.exchange.account.infra.cache.InstrumentCache;
import open.vincentf13.sdk.core.OpenBigDecimal;
import open.vincentf13.sdk.core.exception.OpenException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Service
@Validated
@RequiredArgsConstructor
public class AccountTransactionDomainService {
    
    private static final int OPTIMISTIC_LOCK_MAX_RETRIES = 3;
    
    private final AccountBalanceRepository accountBalanceRepository;
    private final AccountEntryRepository accountEntryRepository;
    private final PlatformAccountRepository platformAccountRepository;
    private final PlatformBalanceRepository platformBalanceRepository;
    private final DefaultIdGenerator idGenerator;
    private final AccountEventPublisher accountEventPublisher;
    private final InstrumentCache instrumentCache;

    private static final BigDecimal CONTRACT_MULTIPLIER = BigDecimal.ONE;
    
    public AccountDepositResult deposit(@NotNull @Valid AccountDepositRequest request) {
        AccountType accountType = AccountType.SPOT_MAIN;
        AssetSymbol normalizedAsset = AccountBalance.normalizeAsset(request.asset());
        AccountBalance userBalance = accountBalanceRepository.getOrCreate(request.userId(), accountType, null, normalizedAsset);
        AccountBalance balanceUpdated = retryUpdateForDeposit(userBalance, request.amount(), request.userId(), normalizedAsset);
        
        PlatformAccount platformAccount = platformAccountRepository.getOrCreate(
                PlatformAccountCode.USER_DEPOSIT,
                PlatformAccountCategory.LIABILITY,
                PlatformAccountStatus.ACTIVE);
        PlatformBalance platformBalance = platformBalanceRepository.getOrCreate(platformAccount.getAccountId(), platformAccount.getAccountCode(), normalizedAsset);
        PlatformBalance platformBalanceUpdated = retryUpdateForPlatformDeposit(platformBalance, request.amount(), normalizedAsset);
        
        Long userEntryId = idGenerator.newLong();
        Long platformEntryId = idGenerator.newLong();
        
        AccountEntry userEntry = AccountEntry.userDeposit(
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
        accountEntryRepository.insert(userEntry);
        
        AccountEntry platformEntry = AccountEntry.platformDeposit(
                platformEntryId,
                platformBalanceUpdated.getAccountId(),
                platformBalanceUpdated.getAsset(),
                request.amount(),
                userEntryId,
                platformBalanceUpdated.getBalance(),
                request.txId(),
                request.creditedAt()
                                                               );
        accountEntryRepository.insert(platformEntry);
        
        return new AccountDepositResult(userEntry, platformEntry, balanceUpdated, platformBalanceUpdated);
    }
    
    public AccountWithdrawalResult withdraw(@NotNull @Valid AccountWithdrawalRequest request) {
        AccountType accountType = AccountType.SPOT_MAIN;
        AssetSymbol normalizedAsset = AccountBalance.normalizeAsset(request.asset());
        AccountBalance userBalance = accountBalanceRepository.getOrCreate(request.userId(), accountType, null, normalizedAsset);
        
        AccountBalance balanceUpdated = retryUpdateForWithdrawal(userBalance, request.amount(), request.userId(), normalizedAsset);
        
        PlatformAccount platformAccount = platformAccountRepository.getOrCreate(
                PlatformAccountCode.USER_DEPOSIT,
                PlatformAccountCategory.LIABILITY,
                PlatformAccountStatus.ACTIVE);
        PlatformBalance platformBalance = platformBalanceRepository.getOrCreate(platformAccount.getAccountId(), platformAccount.getAccountCode(), normalizedAsset);
        PlatformBalance platformBalanceUpdated = retryUpdateForPlatformWithdrawal(platformBalance, request.amount(), normalizedAsset);
        
        Long userEntryId = idGenerator.newLong();
        Long platformEntryId = idGenerator.newLong();
        
        AccountEntry userEntry = AccountEntry.userWithdrawal(
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
        accountEntryRepository.insert(userEntry);
        
        AccountEntry platformEntry = AccountEntry.platformWithdrawal(
                platformEntryId,
                platformBalanceUpdated.getAccountId(),
                platformBalanceUpdated.getAsset(),
                request.amount(),
                userEntryId,
                platformBalanceUpdated.getBalance(),
                request.txId(),
                request.creditedAt()
                                                                  );
        accountEntryRepository.insert(platformEntry);
        
        return new AccountWithdrawalResult(userEntry, balanceUpdated);
    }
    
    public AccountEntry freezeForOrder(@NotNull Long orderId,
                                      @NotNull Long userId,
                                      @NotNull AssetSymbol asset,
                                      @NotNull @DecimalMin(value = ValidationConstant.Names.AMOUNT_MIN) BigDecimal requiredMargin,
                                      Instant eventTime) {
        Instant entryEventTime = eventTime == null ? Instant.now() : eventTime;
        AssetSymbol normalizedAsset = AccountBalance.normalizeAsset(asset);
        String referenceId = orderId.toString();
        Optional<AccountEntry> existing = accountEntryRepository.findOne(
                Wrappers.lambdaQuery(AccountEntryPO.class)
                        .eq(AccountEntryPO::getReferenceType, ReferenceType.ORDER)
                        .eq(AccountEntryPO::getReferenceId, referenceId)
                        .eq(AccountEntryPO::getEntryType, EntryType.FREEZE));
        if (existing.isPresent()) {
            return existing.get();
        }
        AccountBalance userBalance = accountBalanceRepository.getOrCreate(userId, AccountType.SPOT_MAIN, null, normalizedAsset);
        AccountBalance updatedBalance = retryUpdateForFreeze(userBalance, requiredMargin, userId, normalizedAsset, orderId);
        Long freezeEntryId = idGenerator.newLong();
        Long reservedEntryId = idGenerator.newLong();
        AccountEntry freezeEntry = AccountEntry.userFundsFreeze(freezeEntryId,
                                                              updatedBalance.getAccountId(),
                                                              userId,
                                                              normalizedAsset,
                                                              requiredMargin,
                                                              updatedBalance.getBalance(),
                                                              orderId,
                                                              reservedEntryId,
                                                              entryEventTime);
        AccountEntry reservedEntry = AccountEntry.userFundsReserved(reservedEntryId,
                                                                  updatedBalance.getAccountId(),
                                                                  userId,
                                                                  normalizedAsset,
                                                                  requiredMargin,
                                                                  updatedBalance.getBalance(),
                                                                  orderId,
                                                                  freezeEntryId,
                                                                  entryEventTime);
        accountEntryRepository.insert(freezeEntry);
        accountEntryRepository.insert(reservedEntry);
        return freezeEntry;
    }
    
    @Transactional
    public void settleTrade(@NotNull @Valid TradeExecutedEvent event,
                            @NotNull @Valid OrderResponse order,
                            boolean isMaker) {
        AssetSymbol normalizedAsset = AccountBalance.normalizeAsset(event.quoteAsset());
        String referenceId = event.tradeId() + ":" + (isMaker ? "maker" : "taker");
        
        Optional<AccountEntry> existing = accountEntryRepository.findOne(
                Wrappers.lambdaQuery(AccountEntryPO.class)
                        .eq(AccountEntryPO::getReferenceType, ReferenceType.TRADE)
                        .eq(AccountEntryPO::getReferenceId, referenceId));
        if (existing.isPresent()) {
            return;
        }
        
        if (order.intent() == null) {
            throw OpenException.of(AccountErrorCode.ORDER_INTENT_NULL,
                                   Map.of("orderId", order.orderId()));
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
                throw OpenException.of(AccountErrorCode.UNSUPPORTED_ORDER_INTENT,
                                       Map.of("orderId", order.orderId(), "intent", order.intent()));
        }
    }
    
    private void processOpenPosition(TradeExecutedEvent event,
                                     OrderResponse order,
                                     boolean isMaker,
                                     AssetSymbol normalizedAsset,
                                     String referenceId) {
        BigDecimal fee = isMaker ? event.makerFee() : event.takerFee();
        BigDecimal tradeValue = event.price().multiply(event.quantity());
        Long userId = isMaker ? event.makerUserId() : event.takerUserId();
        
        // 1. Find original freeze entry to calculate fee difference
        // 下單預扣時， 手續費會以 taker fee (比較高來預收) ，實際成交時，要查當初 AccountTransactionDomainService#freezeForOrder 扣的手續費是多少，把多收的退給用戶
        AccountEntry freezeEntry = accountEntryRepository.findOne(
                                                               Wrappers.lambdaQuery(AccountEntryPO.class)
                                                                       .eq(AccountEntryPO::getReferenceType, ReferenceType.ORDER)
                                                                       .eq(AccountEntryPO::getReferenceId, order.orderId().toString())
                                                                       .eq(AccountEntryPO::getEntryType, EntryType.FREEZE))
                                                       .orElseThrow(() -> OpenException.of(AccountErrorCode.FREEZE_ENTRY_NOT_FOUND,
                                                                                           Map.of("orderId", order.orderId())));
        
        BigDecimal frozenAmount = freezeEntry.getAmount();
        BigDecimal estimatedFee = frozenAmount.subtract(tradeValue);
        // 預扣手續費餘額 - 實收手續費，若為負則有超收手續費，代表下單通過後調高了手續費率，也退。
        BigDecimal feeRefund = estimatedFee.subtract(fee).abs();
        BigDecimal actualFee = fee.subtract(feeRefund);
        BigDecimal cost = frozenAmount.subtract(feeRefund);
        
        // 2. Update SPOT_MAIN
        // 扣除預扣，並返還手續費
        AccountBalance spotBalance = accountBalanceRepository.getOrCreate(userId, AccountType.SPOT_MAIN, null, normalizedAsset);
        AccountBalance updatedSpotBalance = unfreezeAndDebitSpotBalance(spotBalance, frozenAmount, feeRefund, userId, normalizedAsset, event.tradeId());
        
        // 3. Update ISOLATED_MARGIN
        AccountBalance marginBalance = accountBalanceRepository.getOrCreate(userId, AccountType.ISOLATED_MARGIN, event.instrumentId(), normalizedAsset);
        AccountBalance updatedMarginBalance = retryUpdateForDeposit(marginBalance, tradeValue, userId, normalizedAsset);
        
        // 4. Platform account for fee revenue
        PlatformAccount feeRevenueAccount = platformAccountRepository.getOrCreate(PlatformAccountCode.FEE_REVENUE, PlatformAccountCategory.REVENUE, PlatformAccountStatus.ACTIVE);
        PlatformBalance feeRevenueBalance = platformBalanceRepository.getOrCreate(feeRevenueAccount.getAccountId(), feeRevenueAccount.getAccountCode(), normalizedAsset);
        PlatformBalance updatedFeeRevenueBalance = retryUpdateForPlatformDeposit(feeRevenueBalance, actualFee, normalizedAsset);
        
        // 5. Record account entries
        Long userSpotDebitEntryId = idGenerator.newLong();
        Long feeRevenueEntryId = idGenerator.newLong();
        Long userMarginCreditEntryId = idGenerator.newLong();
        
        
        accountEntryRepository.insert(AccountEntry.settlement(userSpotDebitEntryId, updatedSpotBalance.getAccountId(), userId, normalizedAsset, cost, Direction.CREDIT, feeRevenueEntryId, updatedSpotBalance.getBalance(), referenceId, order.orderId(), event.instrumentId(), event.executedAt(), EntryType.TRADE_SETTLEMENT_SPOT_MAIN));
        accountEntryRepository.insert(AccountEntry.settlement(userMarginCreditEntryId, updatedMarginBalance.getAccountId(), userId, normalizedAsset, tradeValue, Direction.DEBIT, userSpotDebitEntryId, updatedMarginBalance.getBalance(), referenceId, order.orderId(), event.instrumentId(), event.executedAt(), EntryType.TRADE_SETTLEMENT_ISOLATED_MARGIN));
        accountEntryRepository.insert(AccountEntry.settlement(feeRevenueEntryId, updatedFeeRevenueBalance.getAccountId(), null, normalizedAsset, actualFee, Direction.DEBIT, userSpotDebitEntryId, updatedFeeRevenueBalance.getBalance(), referenceId, order.orderId(), event.instrumentId(), event.executedAt(), EntryType.FEE));
        
        accountEventPublisher.publishAccountEntryCreated(userSpotDebitEntryId, userId, normalizedAsset, cost.negate(), updatedSpotBalance.getBalance(), ReferenceType.TRADE, referenceId, EntryType.TRADE_SETTLEMENT_SPOT_MAIN, event.instrumentId());
        accountEventPublisher.publishAccountEntryCreated(userMarginCreditEntryId, userId, normalizedAsset, tradeValue, updatedMarginBalance.getBalance(), ReferenceType.TRADE, referenceId, EntryType.TRADE_SETTLEMENT_ISOLATED_MARGIN, event.instrumentId());
        accountEventPublisher.publishAccountEntryCreated(feeRevenueEntryId, null, normalizedAsset, actualFee, updatedFeeRevenueBalance.getBalance(), ReferenceType.TRADE, referenceId, EntryType.FEE, event.instrumentId());
    }
    
    private void processClosePosition(TradeExecutedEvent event,
                                      OrderResponse order,
                                      boolean isMaker,
                                      AssetSymbol normalizedAsset,
                                      String referenceId) {
        BigDecimal fee = isMaker ? event.makerFee() : event.takerFee();
        Long userId = isMaker ? event.makerUserId() : event.takerUserId();

        BigDecimal contractMultiplier = instrumentCache.get(event.instrumentId())
                .map(instrument -> instrument.contractSize() != null ? instrument.contractSize() : CONTRACT_MULTIPLIER)
                .orElse(CONTRACT_MULTIPLIER);

        BigDecimal costBasis = order.closingEntryPrice().multiply(event.quantity()).multiply(contractMultiplier);
        BigDecimal grossRealizedPnl;
        if (order.side() == OrderSide.BUY) { // Closing a SHORT position
            grossRealizedPnl = order.closingEntryPrice().subtract(event.price()).multiply(event.quantity()).multiply(contractMultiplier);
        } else { // Closing a LONG position (order.side() == OrderSide.SELL)
            grossRealizedPnl = event.price().subtract(order.closingEntryPrice()).multiply(event.quantity()).multiply(contractMultiplier);
        }
        BigDecimal realizedPnl = grossRealizedPnl.subtract(fee);
        
        // 手續費扣完可能是負的，帳戶不夠付手續費，就以負餘額表示。
        BigDecimal totalDepositAmount = costBasis.add(realizedPnl);
        
        // 1. Update ISOLATED_MARGIN
        AccountBalance marginBalance = accountBalanceRepository.getOrCreate(userId, AccountType.ISOLATED_MARGIN, event.instrumentId(), normalizedAsset);
        AccountBalance updatedMarginBalance = retryUpdateForWithdrawalWithPnl(marginBalance, costBasis, realizedPnl, userId, normalizedAsset);
        
        // 2. Update SPOT_MAIN
        AccountBalance spotBalance = accountBalanceRepository.getOrCreate(userId, AccountType.SPOT_MAIN, null, normalizedAsset);
        AccountBalance updatedSpotBalance = retryUpdateForDepositWithPnl(spotBalance, totalDepositAmount, realizedPnl, userId, normalizedAsset);
        
        // 3. Platform account for fee revenue
        PlatformAccount feeRevenueAccount = platformAccountRepository.getOrCreate(PlatformAccountCode.FEE_REVENUE, PlatformAccountCategory.REVENUE, PlatformAccountStatus.ACTIVE);
        PlatformBalance feeRevenueBalance = platformBalanceRepository.getOrCreate(feeRevenueAccount.getAccountId(), feeRevenueAccount.getAccountCode(), normalizedAsset);
        PlatformBalance updatedFeeRevenueBalance = retryUpdateForPlatformDeposit(feeRevenueBalance, fee, normalizedAsset);
        
        // 4. Record account entries
        Long userMarginDebitEntryId = idGenerator.newLong();
        Long userSpotCreditEntryId = idGenerator.newLong();
        Long feeRevenueEntryId = idGenerator.newLong();
        
        
        accountEntryRepository.insert(AccountEntry.settlement(userMarginDebitEntryId, updatedMarginBalance.getAccountId(), userId, normalizedAsset, costBasis, Direction.CREDIT, userSpotCreditEntryId, updatedMarginBalance.getBalance(), referenceId, order.orderId(), event.instrumentId(), event.executedAt(), EntryType.TRADE_SETTLEMENT_ISOLATED_MARGIN));
        // 盈虧要另外一筆分錄，借貸才能平衡 (對應另一個用戶的損益)
        accountEntryRepository.insert(AccountEntry.settlement(userSpotCreditEntryId, updatedSpotBalance.getAccountId(), userId, normalizedAsset, costBasis, Direction.DEBIT, userMarginDebitEntryId,
                                                            updatedSpotBalance.getBalance().subtract(realizedPnl),
                                                            referenceId, order.orderId(), event.instrumentId(), event.executedAt(), EntryType.TRADE_SETTLEMENT_SPOT_MAIN));
        accountEntryRepository.insert(AccountEntry.settlement(userSpotCreditEntryId, updatedSpotBalance.getAccountId(), userId, normalizedAsset, realizedPnl,
                                                            realizedPnl.compareTo(BigDecimal.ZERO) > 0 ? Direction.DEBIT : Direction.CREDIT,
                                                            userMarginDebitEntryId, updatedSpotBalance.getBalance(), referenceId, order.orderId(), event.instrumentId(), event.executedAt(), EntryType.TRADE_SETTLEMENT_SPOT_MAIN_PNL));
        
        accountEntryRepository.insert(AccountEntry.settlement(feeRevenueEntryId, updatedFeeRevenueBalance.getAccountId(), null, normalizedAsset, fee, Direction.DEBIT, userSpotCreditEntryId, updatedFeeRevenueBalance.getBalance(), referenceId, order.orderId(), event.instrumentId(), event.executedAt(), EntryType.FEE));
        
        
        accountEventPublisher.publishAccountEntryCreated(userMarginDebitEntryId, userId, normalizedAsset, costBasis.negate(), updatedMarginBalance.getBalance(), ReferenceType.TRADE, referenceId, EntryType.TRADE_SETTLEMENT_ISOLATED_MARGIN, event.instrumentId());
        accountEventPublisher.publishAccountEntryCreated(userSpotCreditEntryId, userId, normalizedAsset, costBasis,
                                                       updatedSpotBalance.getBalance().subtract(realizedPnl),
                                                       ReferenceType.TRADE, referenceId, EntryType.TRADE_SETTLEMENT_SPOT_MAIN, event.instrumentId());
        accountEventPublisher.publishAccountEntryCreated(userSpotCreditEntryId, userId, normalizedAsset, realizedPnl,
                                                       updatedSpotBalance.getBalance(),
                                                       ReferenceType.TRADE, referenceId, EntryType.TRADE_SETTLEMENT_SPOT_MAIN_PNL, event.instrumentId());
        accountEventPublisher.publishAccountEntryCreated(feeRevenueEntryId, null, normalizedAsset, fee, updatedFeeRevenueBalance.getBalance(), ReferenceType.TRADE, referenceId, EntryType.FEE, event.instrumentId());
    }
    
    
    private AccountBalance retryUpdateForFreeze(AccountBalance balance,
                                               BigDecimal amount,
                                               Long userId,
                                               AssetSymbol asset,
                                               Long orderId) {
        AccountBalance current = balance;
        int attempts = 0;
        while (attempts < OPTIMISTIC_LOCK_MAX_RETRIES) {
            int expectedVersion = current.safeVersion();
            BigDecimal available = OpenBigDecimal.safeDecimal(current.getAvailable());
            if (available.compareTo(amount) < 0) {
                throw OpenException.of(AccountErrorCode.INSUFFICIENT_FUNDS,
                                       Map.of("userId", userId, "asset", asset.code(), "available", available, "required", amount));
            }
            BigDecimal reserved = OpenBigDecimal.safeDecimal(current.getReserved());
            current.setAvailable(available.subtract(amount));
            current.setReserved(reserved.add(amount));
            current.setVersion(expectedVersion + 1);
            boolean updated = accountBalanceRepository.updateSelectiveBy(
                    current,
                    new LambdaUpdateWrapper<AccountBalancePO>()
                            .eq(AccountBalancePO::getId, current.getId())
                            .eq(AccountBalancePO::getVersion, expectedVersion)
                                                                       );
            if (updated) {
                return current;
            }
            attempts++;
            current = reloadAccountBalance(current.getUserId(), current.getAccountType(), current.getInstrumentId(), asset);
        }
        throw OpenException.of(AccountErrorCode.OPTIMISTIC_LOCK_FAILURE,
                               Map.of("userId", userId, "orderId", orderId, "operation", "freeze"));
    }
    
    private AccountBalance unfreezeAndDebitSpotBalance(AccountBalance balance,
                                                      BigDecimal amountToDebit,
                                                      BigDecimal amountToRefund,
                                                      Long userId,
                                                      AssetSymbol asset,
                                                      Long tradeId) {
        AccountBalance current = balance;
        int attempts = 0;
        while (attempts < OPTIMISTIC_LOCK_MAX_RETRIES) {
            int expectedVersion = current.safeVersion();
            BigDecimal reserved = OpenBigDecimal.safeDecimal(current.getReserved());
            BigDecimal cost = amountToDebit.subtract(amountToRefund);
            if (reserved.compareTo(amountToDebit) < 0) {
                throw OpenException.of(AccountErrorCode.INSUFFICIENT_RESERVED_BALANCE,
                                       Map.of("userId", userId, "tradeId", tradeId, "reserved", reserved, "required", amountToDebit));
            }
            
            current.setReserved(reserved.subtract(amountToDebit));
            current.setBalance(current.getBalance().subtract(cost));
            current.setAvailable(current.getAvailable().add(amountToRefund));
            current.setVersion(expectedVersion + 1);
            boolean updated = accountBalanceRepository.updateSelectiveBy(
                    current,
                    new LambdaUpdateWrapper<AccountBalancePO>()
                            .eq(AccountBalancePO::getId, current.getId())
                            .eq(AccountBalancePO::getVersion, expectedVersion)
                                                                       );
            if (updated) {
                return current;
            }
            attempts++;
            current = reloadAccountBalance(current.getAccountId(), asset);
        }
        throw OpenException.of(AccountErrorCode.OPTIMISTIC_LOCK_FAILURE,
                               Map.of("userId", userId, "tradeId", tradeId, "operation", "unfreezeAndDebit"));
    }
    
    private AccountBalance retryUpdateForDeposit(AccountBalance balance,
                                                BigDecimal amount,
                                                Long userId,
                                                AssetSymbol asset) {
        AccountBalance current = balance;
        int attempts = 0;
        while (attempts < OPTIMISTIC_LOCK_MAX_RETRIES) {
            int expectedVersion = current.safeVersion();
            current.setBalance(current.getBalance().add(amount));
            current.setAvailable(current.getAvailable().add(amount));
            current.setTotalDeposited(current.getTotalDeposited().add(amount));
            current.setVersion(expectedVersion + 1);
            boolean updated = accountBalanceRepository.updateSelectiveBy(
                    current,
                    new LambdaUpdateWrapper<AccountBalancePO>()
                            .eq(AccountBalancePO::getId, current.getId())
                            .eq(AccountBalancePO::getVersion, expectedVersion)
                                                                       );
            if (updated) {
                return current;
            }
            attempts++;
            current = reloadAccountBalance(current.getUserId(), current.getAccountType(), current.getInstrumentId(), asset);
        }
        throw OpenException.of(AccountErrorCode.OPTIMISTIC_LOCK_FAILURE,
                               Map.of("userId", userId, "operation", "deposit"));
    }
    
    private AccountBalance retryUpdateForDepositWithPnl(AccountBalance balance,
                                                       BigDecimal amount,
                                                       BigDecimal realizedPnl,
                                                       Long userId,
                                                       AssetSymbol asset) {
        AccountBalance current = balance;
        int attempts = 0;
        while (attempts < OPTIMISTIC_LOCK_MAX_RETRIES) {
            int expectedVersion = current.safeVersion();
            current.setBalance(current.getBalance().add(amount));
            current.setAvailable(current.getAvailable().add(amount));
            current.setTotalPnl(OpenBigDecimal.safeDecimal(current.getTotalPnl()).add(realizedPnl));
            current.setVersion(expectedVersion + 1);
            boolean updated = accountBalanceRepository.updateSelectiveBy(
                    current,
                    new LambdaUpdateWrapper<AccountBalancePO>()
                            .eq(AccountBalancePO::getId, current.getId())
                            .eq(AccountBalancePO::getVersion, expectedVersion)
                                                                       );
            if (updated) {
                return current;
            }
            attempts++;
            current = reloadAccountBalance(current.getUserId(), current.getAccountType(), current.getInstrumentId(), asset);
        }
        throw OpenException.of(AccountErrorCode.OPTIMISTIC_LOCK_FAILURE,
                               Map.of("userId", userId, "operation", "depositWithPnl"));
    }
    
    private AccountBalance retryUpdateForWithdrawal(AccountBalance balance,
                                                   BigDecimal amount,
                                                   Long userId,
                                                   AssetSymbol asset) {
        AccountBalance current = balance;
        int attempts = 0;
        while (attempts < OPTIMISTIC_LOCK_MAX_RETRIES) {
            int expectedVersion = current.safeVersion();
            BigDecimal available = OpenBigDecimal.safeDecimal(current.getAvailable());
            if (available.compareTo(amount) < 0) {
                throw OpenException.of(AccountErrorCode.INSUFFICIENT_FUNDS,
                                       Map.of("userId", userId, "asset", asset.code(), "available", available, "required", amount));
            }
            current.setAvailable(available.subtract(amount));
            current.setBalance(current.getBalance().subtract(amount));
            current.setTotalWithdrawn(current.getTotalWithdrawn().add(amount));
            current.setVersion(expectedVersion + 1);
            boolean updated = accountBalanceRepository.updateSelectiveBy(
                    current,
                    new LambdaUpdateWrapper<AccountBalancePO>()
                            .eq(AccountBalancePO::getId, current.getId())
                            .eq(AccountBalancePO::getVersion, expectedVersion)
                                                                       );
            if (updated) {
                return current;
            }
            attempts++;
            current = reloadAccountBalance(current.getUserId(), current.getAccountType(), current.getInstrumentId(), asset);
        }
        throw OpenException.of(AccountErrorCode.OPTIMISTIC_LOCK_FAILURE,
                               Map.of("userId", userId, "operation", "updateBalance"));
    }
    
    private AccountBalance retryUpdateForWithdrawalWithPnl(AccountBalance balance,
                                                          BigDecimal amount,
                                                          BigDecimal realizedPnl,
                                                          Long userId,
                                                          AssetSymbol asset) {
        AccountBalance current = balance;
        int attempts = 0;
        while (attempts < OPTIMISTIC_LOCK_MAX_RETRIES) {
            int expectedVersion = current.safeVersion();
            BigDecimal available = OpenBigDecimal.safeDecimal(current.getAvailable());
            if (available.compareTo(amount) < 0) {
                throw OpenException.of(AccountErrorCode.INSUFFICIENT_FUNDS,
                                       Map.of("userId", userId, "asset", asset.code(), "available", available, "required", amount));
            }
            current.setAvailable(available.subtract(amount));
            current.setBalance(current.getBalance().subtract(amount));
            current.setTotalWithdrawn(current.getTotalWithdrawn().add(amount));
            current.setTotalPnl(OpenBigDecimal.safeDecimal(current.getTotalPnl()).add(realizedPnl));
            current.setVersion(expectedVersion + 1);
            boolean updated = accountBalanceRepository.updateSelectiveBy(
                    current,
                    new LambdaUpdateWrapper<AccountBalancePO>()
                            .eq(AccountBalancePO::getId, current.getId())
                            .eq(AccountBalancePO::getVersion, expectedVersion)
                                                                       );
            if (updated) {
                return current;
            }
            attempts++;
            current = reloadAccountBalance(current.getUserId(), current.getAccountType(), current.getInstrumentId(), asset);
        }
        throw OpenException.of(AccountErrorCode.OPTIMISTIC_LOCK_FAILURE,
                               Map.of("userId", userId, "operation", "updateBalance"));
    }
    
    private AccountBalance reloadAccountBalance(Long userId,
                                              AccountType accountType,
                                              Long instrumentId,
                                              AssetSymbol asset) {
        return accountBalanceRepository.findOne(
                                              Wrappers.lambdaQuery(AccountBalancePO.class)
                                                      .eq(AccountBalancePO::getUserId, userId)
                                                      .eq(AccountBalancePO::getAccountType, accountType)
                                                      .eq(AccountBalancePO::getInstrumentId, instrumentId)
                                                      .eq(AccountBalancePO::getAsset, asset))
                                      .orElseThrow(() -> OpenException.of(AccountErrorCode.ACCOUNT_BALANCE_NOT_FOUND,
                                                                          Map.of("userId", userId, "asset", asset.code())));
    }
    
    private AccountBalance reloadAccountBalance(Long accountId,
                                              AssetSymbol asset) {
        return accountBalanceRepository.findOne(
                                              Wrappers.lambdaQuery(AccountBalancePO.class)
                                                      .eq(AccountBalancePO::getAccountId, accountId)
                                                      .eq(AccountBalancePO::getAsset, asset))
                                      .orElseThrow(() -> OpenException.of(AccountErrorCode.ACCOUNT_BALANCE_NOT_FOUND,
                                                                          Map.of("accountId", accountId, "asset", asset.code())));
    }
    
    
    private PlatformBalance retryUpdateForPlatformDeposit(PlatformBalance platformBalance,
                                                          BigDecimal amount,
                                                          AssetSymbol asset) {
        PlatformBalance current = platformBalance;
        int attempts = 0;
        while (attempts < OPTIMISTIC_LOCK_MAX_RETRIES) {
            int expectedVersion = current.safeVersion();
            BigDecimal currentBalance = OpenBigDecimal.safeDecimal(current.getBalance());
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
        throw OpenException.of(AccountErrorCode.OPTIMISTIC_LOCK_FAILURE,
                               Map.of("accountId", platformBalance.getAccountId(), "operation", "updatePlatformBalance"));
    }
    
    private PlatformBalance retryUpdateForPlatformWithdrawal(PlatformBalance platformBalance,
                                                             BigDecimal amount,
                                                             AssetSymbol asset) {
        PlatformBalance current = platformBalance;
        int attempts = 0;
        while (attempts < OPTIMISTIC_LOCK_MAX_RETRIES) {
            int expectedVersion = current.safeVersion();
            BigDecimal currentBalance = OpenBigDecimal.safeDecimal(current.getBalance());
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
        throw OpenException.of(AccountErrorCode.OPTIMISTIC_LOCK_FAILURE,
                               Map.of("accountId", platformBalance.getAccountId(), "operation", "updatePlatformBalance"));
    }
    
    private PlatformBalance reloadPlatformBalance(Long accountId,
                                                  PlatformAccountCode accountCode,
                                                  AssetSymbol asset) {
        return platformBalanceRepository.findOne(
                                                Wrappers.lambdaQuery(PlatformBalancePO.class)
                                                        .eq(PlatformBalancePO::getAccountId, accountId)
                                                        .eq(PlatformBalancePO::getAccountCode, accountCode)
                                                        .eq(PlatformBalancePO::getAsset, asset))
                                        .orElseThrow(() -> OpenException.of(AccountErrorCode.PLATFORM_BALANCE_NOT_FOUND,
                                                                            Map.of("accountId", accountId, "asset", asset.code())));
    }
    
}
