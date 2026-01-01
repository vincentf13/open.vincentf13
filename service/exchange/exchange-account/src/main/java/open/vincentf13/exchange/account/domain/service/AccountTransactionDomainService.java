package open.vincentf13.exchange.account.domain.service;

import com.github.yitter.idgen.DefaultIdGenerator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.domain.model.PlatformAccount;
import open.vincentf13.exchange.account.domain.model.PlatformJournal;
import open.vincentf13.exchange.account.domain.model.UserAccount;
import open.vincentf13.exchange.account.domain.model.UserJournal;
import open.vincentf13.exchange.account.infra.AccountErrorCode;
import open.vincentf13.exchange.account.infra.AccountEvent;
import open.vincentf13.exchange.account.infra.messaging.publisher.TradeSettlementEventPublisher;
import open.vincentf13.exchange.account.infra.persistence.repository.PlatformAccountRepository;
import open.vincentf13.exchange.account.infra.persistence.repository.PlatformJournalRepository;
import open.vincentf13.exchange.account.infra.persistence.repository.UserAccountRepository;
import open.vincentf13.exchange.account.infra.persistence.repository.UserJournalRepository;
import open.vincentf13.exchange.account.infra.cache.InstrumentCache;
import open.vincentf13.exchange.account.sdk.mq.event.TradeMarginSettledEvent;
import open.vincentf13.exchange.account.sdk.rest.api.dto.AccountDepositRequest;
import open.vincentf13.exchange.account.sdk.rest.api.dto.AccountWithdrawalRequest;
import open.vincentf13.exchange.account.sdk.rest.api.enums.PlatformAccountCode;
import open.vincentf13.exchange.account.sdk.rest.api.enums.ReferenceType;
import open.vincentf13.exchange.account.sdk.rest.api.enums.UserAccountCode;
import open.vincentf13.exchange.admin.contract.dto.InstrumentSummaryResponse;
import open.vincentf13.exchange.common.sdk.enums.AssetSymbol;
import open.vincentf13.exchange.common.sdk.enums.Direction;
import open.vincentf13.exchange.common.sdk.enums.PositionIntentType;
import open.vincentf13.exchange.matching.sdk.mq.event.TradeExecutedEvent;
import open.vincentf13.exchange.order.mq.event.FundsFreezeRequestedEvent;
import open.vincentf13.sdk.core.OpenValidator;
import open.vincentf13.sdk.core.exception.OpenException;
import open.vincentf13.sdk.core.log.OpenLog;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@Validated
@RequiredArgsConstructor
public class AccountTransactionDomainService {
    
    private final UserAccountRepository userAccountRepository;
    private final PlatformAccountRepository platformAccountRepository;
    private final UserJournalRepository userJournalRepository;
    private final PlatformJournalRepository platformJournalRepository;
    private final TradeSettlementEventPublisher tradeSettlementEventPublisher;
    private final InstrumentCache instrumentCache;
    private final DefaultIdGenerator idGenerator;
    
    public record FundsFreezeResult(AssetSymbol asset, BigDecimal amount) {
    }

    public record AccountDepositResult(
            UserAccount userAssetAccount,
            UserAccount userEquityAccount,
            PlatformAccount platformAssetAccount,
            PlatformAccount platformLiabilityAccount,
            UserJournal userAssetJournal,
            UserJournal userEquityJournal,
            PlatformJournal platformAssetJournal,
            PlatformJournal platformLiabilityJournal
    ) {
    }

    public record AccountWithdrawalResult(
            UserAccount userAssetAccount,
            UserAccount userEquityAccount,
            PlatformAccount platformAssetAccount,
            PlatformAccount platformLiabilityAccount,
            UserJournal userAssetJournal,
            UserJournal userEquityJournal,
            PlatformJournal platformAssetJournal,
            PlatformJournal platformLiabilityJournal
    ) {
    }
    
    @Transactional(rollbackFor = Exception.class)
    public AccountDepositResult deposit(@NotNull @Valid AccountDepositRequest request) {
        
        AssetSymbol asset = UserAccount.normalizeAsset(request.getAsset());
        Instant eventTime = request.getCreditedAt() == null ? Instant.now() : request.getCreditedAt();
        
        UserAccount userAssetAccount = userAccountRepository.getOrCreate(
                request.getUserId(),
                UserAccountCode.SPOT,
                null,
                asset);
        
        assertNoDuplicateJournal(userAssetAccount.getAccountId(), asset, ReferenceType.DEPOSIT, request.getTxId());
        
        UserAccount userEquityAccount = userAccountRepository.getOrCreate(
                request.getUserId(),
                UserAccountCode.SPOT_EQUITY,
                null,
                asset);
        
        PlatformAccount platformAssetAccount = platformAccountRepository.getOrCreate(
                PlatformAccountCode.HOT_WALLET,
                asset);
        PlatformAccount platformLiabilityAccount = platformAccountRepository.getOrCreate(
                PlatformAccountCode.USER_LIABILITY,
                asset);
        
        UserAccount updatedUserAsset = applyUserUpdate(userAssetAccount, Direction.DEBIT, request.getAmount());
        UserAccount updatedUserEquity = applyUserUpdate(userEquityAccount, Direction.CREDIT, request.getAmount());
        PlatformAccount updatedPlatformAsset = applyPlatformUpdate(platformAssetAccount, Direction.DEBIT, request.getAmount());
        PlatformAccount updatedPlatformLiability = applyPlatformUpdate(platformLiabilityAccount, Direction.CREDIT, request.getAmount());
        
        UserJournal userAssetJournal = buildUserJournal(updatedUserAsset, Direction.DEBIT, request.getAmount(), ReferenceType.DEPOSIT, request.getTxId(), "User deposit", eventTime);
        UserJournal userEquityJournal = buildUserJournal(updatedUserEquity, Direction.CREDIT, request.getAmount(), ReferenceType.DEPOSIT, request.getTxId(), "User equity increase", eventTime);
        PlatformJournal platformAssetJournal = buildPlatformJournal(updatedPlatformAsset, Direction.DEBIT, request.getAmount(), ReferenceType.DEPOSIT, request.getTxId(), "Platform asset increase", eventTime);
        PlatformJournal platformLiabilityJournal = buildPlatformJournal(updatedPlatformLiability, Direction.CREDIT, request.getAmount(), ReferenceType.DEPOSIT, request.getTxId(), "Platform liability increase", eventTime);
        
        userAccountRepository.updateSelectiveBatch(
                List.of(updatedUserAsset, updatedUserEquity),
                List.of(userAssetAccount.safeVersion(), userEquityAccount.safeVersion()),
                "deposit");
        platformAccountRepository.updateSelectiveBatch(
                List.of(updatedPlatformAsset, updatedPlatformLiability),
                List.of(platformAssetAccount.safeVersion(), platformLiabilityAccount.safeVersion()),
                "deposit");
        
        userJournalRepository.insertBatch(List.of(userAssetJournal, userEquityJournal));
        platformJournalRepository.insertBatch(List.of(platformAssetJournal, platformLiabilityJournal));
        
        return new AccountDepositResult(updatedUserAsset, updatedUserEquity, updatedPlatformAsset, updatedPlatformLiability,
                userAssetJournal, userEquityJournal, platformAssetJournal, platformLiabilityJournal);
    }

    @Transactional(rollbackFor = Exception.class)
    public FundsFreezeResult freezeForOrder(@NotNull @Valid FundsFreezeRequestedEvent event,
                                            @NotNull InstrumentSummaryResponse instrument) {
        OpenValidator.validateOrThrow(event);
        AssetSymbol asset = instrument.quoteAsset();
        BigDecimal amount = event.requiredMargin().add(event.fee());
        
        UserAccount userSpot = userAccountRepository.getOrCreate(event.userId(), UserAccountCode.SPOT, null, asset);
        assertNoDuplicateJournal(userSpot.getAccountId(), asset, ReferenceType.FUNDS_FREEZE_REQUESTED, event.orderId().toString());
        
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw OpenException.of(AccountErrorCode.INVALID_AMOUNT, Map.of("orderId", event.orderId(), "amount", amount));
        }
        if (!userSpot.hasEnoughAvailable(amount)) {
            throw OpenException.of(AccountErrorCode.INSUFFICIENT_FUNDS,
                                   Map.of("userId", event.userId(), "asset", asset, "available", userSpot.getAvailable(), "amount", amount));
        }
        UserAccount frozenAccount = applyUserFreeze(userSpot, amount);
        userAccountRepository.updateSelectiveBatch(
                List.of(frozenAccount),
                List.of(userSpot.safeVersion()),
                "order-freeze");
        UserJournal reserveJournal = buildUserJournal(
                frozenAccount,
                Direction.DEBIT,
                amount,
                ReferenceType.FUNDS_FREEZE_REQUESTED,
                event.orderId().toString(),
                "Funds reserved for order",
                event.createdAt());
        UserJournal availableJournal = buildUserJournal(
                frozenAccount,
                Direction.CREDIT,
                amount,
                ReferenceType.FUNDS_FREEZE_REQUESTED,
                event.orderId().toString(),
                "Funds moved from available",
                event.createdAt());
        userJournalRepository.insertBatch(List.of(reserveJournal, availableJournal));
        return new FundsFreezeResult(asset, amount);
    }
    
    @Transactional(rollbackFor = Exception.class)
    public AccountWithdrawalResult withdraw(@NotNull @Valid AccountWithdrawalRequest request) {
        AssetSymbol asset = UserAccount.normalizeAsset(request.getAsset());
        Instant eventTime = request.getCreditedAt() == null ? Instant.now() : request.getCreditedAt();
        
        UserAccount userAssetAccount = userAccountRepository.getOrCreate(
                request.getUserId(),
                UserAccountCode.SPOT,
                null,
                asset);
        
        assertNoDuplicateJournal(userAssetAccount.getAccountId(), asset, ReferenceType.WITHDRAWAL, request.getTxId());
        
        UserAccount userEquityAccount = userAccountRepository.getOrCreate(
                request.getUserId(),
                UserAccountCode.SPOT_EQUITY,
                null,
                asset);
        
        if (!userAssetAccount.hasEnoughAvailable(request.getAmount())) {
            throw OpenException.of(AccountErrorCode.INSUFFICIENT_FUNDS,
                                   Map.of("userId", request.getUserId(), "asset", asset, "available", userAssetAccount.getAvailable(), "amount", request.getAmount()));
        }
        
        PlatformAccount platformAssetAccount = platformAccountRepository.getOrCreate(
                PlatformAccountCode.HOT_WALLET,
                asset);
        PlatformAccount platformLiabilityAccount = platformAccountRepository.getOrCreate(
                PlatformAccountCode.USER_LIABILITY,
                asset);
        
        UserAccount updatedUserAsset = applyUserUpdate(userAssetAccount, Direction.CREDIT, request.getAmount());
        UserAccount updatedUserEquity = applyUserUpdate(userEquityAccount, Direction.DEBIT, request.getAmount());
        PlatformAccount updatedPlatformAsset = applyPlatformUpdate(platformAssetAccount, Direction.CREDIT, request.getAmount());
        PlatformAccount updatedPlatformLiability = applyPlatformUpdate(platformLiabilityAccount, Direction.DEBIT, request.getAmount());
        
        UserJournal userAssetJournal = buildUserJournal(updatedUserAsset, Direction.CREDIT, request.getAmount(), ReferenceType.WITHDRAWAL, request.getTxId(), "User withdrawal", eventTime);
        UserJournal userEquityJournal = buildUserJournal(updatedUserEquity, Direction.DEBIT, request.getAmount(), ReferenceType.WITHDRAWAL, request.getTxId(), "User equity decrease", eventTime);
        PlatformJournal platformAssetJournal = buildPlatformJournal(updatedPlatformAsset, Direction.CREDIT, request.getAmount(), ReferenceType.WITHDRAWAL, request.getTxId(), "Platform payout", eventTime);
        PlatformJournal platformLiabilityJournal = buildPlatformJournal(updatedPlatformLiability, Direction.DEBIT, request.getAmount(), ReferenceType.WITHDRAWAL, request.getTxId(), "Platform liability decrease", eventTime);
        
        userAccountRepository.updateSelectiveBatch(
                List.of(updatedUserAsset, updatedUserEquity),
                List.of(userAssetAccount.safeVersion(), userEquityAccount.safeVersion()),
                "withdrawal");
        platformAccountRepository.updateSelectiveBatch(
                List.of(updatedPlatformAsset, updatedPlatformLiability),
                List.of(platformAssetAccount.safeVersion(), platformLiabilityAccount.safeVersion()),
                "withdrawal");
        
        userJournalRepository.insertBatch(List.of(userAssetJournal, userEquityJournal));
        platformJournalRepository.insertBatch(List.of(platformAssetJournal, platformLiabilityJournal));
        
        return new AccountWithdrawalResult(updatedUserAsset, updatedUserEquity, updatedPlatformAsset, updatedPlatformLiability,
                userAssetJournal, userEquityJournal, platformAssetJournal, platformLiabilityJournal);
    }
    
    private UserAccount applyUserUpdate(UserAccount current,
                                        Direction direction,
                                        BigDecimal amount) {
        return current.apply(direction, amount);
    }
    
    private PlatformAccount applyPlatformUpdate(PlatformAccount current,
                                                Direction direction,
                                                BigDecimal amount) {
        return current.apply(direction, amount);
    }
    
    private UserJournal buildUserJournal(UserAccount account,
                                         Direction direction,
                                         BigDecimal amount,
                                         ReferenceType referenceType,
                                         String referenceId,
                                         String description,
                                         Instant eventTime) {
        return UserJournal.builder()
                          .journalId(idGenerator.newLong())
                          .userId(account.getUserId())
                          .accountId(account.getAccountId())
                          .category(account.getCategory())
                          .asset(account.getAsset())
                          .amount(amount)
                          .direction(direction)
                          .balanceAfter(account.getBalance())
                          .referenceType(referenceType)
                          .referenceId(referenceId)
                          .description(description)
                          .eventTime(eventTime)
                          .build();
    }
    
    private PlatformJournal buildPlatformJournal(PlatformAccount account,
                                                 Direction direction,
                                                 BigDecimal amount,
                                                 ReferenceType referenceType,
                                                 String referenceId,
                                                 String description,
                                                 Instant eventTime) {
        return PlatformJournal.builder()
                              .journalId(idGenerator.newLong())
                              .accountId(account.getAccountId())
                              .category(account.getCategory())
                              .asset(account.getAsset())
                              .amount(amount)
                              .direction(direction)
                              .balanceAfter(account.getBalance())
                              .referenceType(referenceType)
                              .referenceId(referenceId)
                          .description(description)
                          .eventTime(eventTime)
                          .build();
    }

    private void assertNoDuplicateJournal(Long accountId,
                                          AssetSymbol asset,
                                          ReferenceType referenceType,
                                          String referenceId) {
        boolean exists = userJournalRepository.findLatestByReference(accountId, asset, referenceType, referenceId).isPresent();
        if (exists) {
            throw OpenException.of(AccountErrorCode.DUPLICATE_REQUEST,
                                   Map.of("accountId", accountId, "asset", asset, "referenceType", referenceType, "referenceId", referenceId));
        }
    }
    
    private UserAccount applyUserFreeze(UserAccount current,
                                        BigDecimal amount) {
        BigDecimal available = current.getAvailable();
        if (available.compareTo(amount) < 0) {
            throw OpenException.of(AccountErrorCode.INSUFFICIENT_FUNDS,
                                   Map.of("userId", current.getUserId(), "asset", current.getAsset(), "available", available, "amount", amount));
        }
        return UserAccount.builder()
                          .accountId(current.getAccountId())
                          .userId(current.getUserId())
                          .accountCode(current.getAccountCode())
                          .accountName(current.getAccountName())
                          .instrumentId(current.getInstrumentId())
                          .category(current.getCategory())
                          .asset(current.getAsset())
                          .balance(current.getBalance())
                          .available(current.getAvailable().subtract(amount))
                          .reserved(current.getReserved().add(amount))
                          .version(current.safeVersion() + 1)
                          .createdAt(current.getCreatedAt())
                          .build();
    }
    
    private UserAccount applyTradeSettlement(UserAccount current,
                                             BigDecimal totalReserved,
                                             BigDecimal totalUsed,
                                             BigDecimal feeRefund) {
        BigDecimal newReserved = current.getReserved().subtract(totalReserved);
        if (newReserved.signum() < 0) {
            throw OpenException.of(AccountErrorCode.INSUFFICIENT_RESERVED_BALANCE,
                                   Map.of("userId", current.getUserId(), "reserved", current.getReserved(), "required", totalReserved));
        }
        BigDecimal newBalance = current.getBalance().subtract(totalUsed);
        if (newBalance.signum() < 0) {
            throw OpenException.of(AccountErrorCode.INSUFFICIENT_FUNDS,
                                   Map.of("userId", current.getUserId(), "balance", current.getBalance(), "required", totalUsed));
        }
        BigDecimal newAvailable = current.getAvailable().add(feeRefund);
        if (newAvailable.signum() < 0) {
            throw OpenException.of(AccountErrorCode.INSUFFICIENT_FUNDS,
                                   Map.of("userId", current.getUserId(), "available", current.getAvailable(), "required", totalUsed));
        }
        return UserAccount.builder()
                          .accountId(current.getAccountId())
                          .userId(current.getUserId())
                          .accountCode(current.getAccountCode())
                          .accountName(current.getAccountName())
                          .instrumentId(current.getInstrumentId())
                          .category(current.getCategory())
                          .asset(current.getAsset())
                          .balance(newBalance)
                          .available(newAvailable)
                          .reserved(newReserved)
                          .version(current.safeVersion() + 1)
                          .createdAt(current.getCreatedAt())
                          .build();
    }

    @Transactional(rollbackFor = Exception.class)
    public void settleTrade(@NotNull @Valid TradeExecutedEvent event,
                            @NotNull Long orderId,
                            @NotNull Long userId,
                            @NotNull PositionIntentType intent,
                            boolean isMaker) {
        AssetSymbol asset = event.quoteAsset();
        UserAccount userSpot = userAccountRepository.getOrCreate(userId, UserAccountCode.SPOT, null, asset);
        
        String refIdWithSide = event.tradeId() + (isMaker ? ":Maker" : ":Taker");
        assertNoDuplicateJournal(userSpot.getAccountId(), asset, ReferenceType.TRADE_MARGIN_SETTLED, refIdWithSide);
        if (intent != PositionIntentType.INCREASE) {
            throw OpenException.of(AccountErrorCode.UNSUPPORTED_ORDER_INTENT,
                                   Map.of("orderId", orderId, "intent", intent));
        }
        BigDecimal contractMultiplier = instrumentCache.get(event.instrumentId())
                                                       .map(instrument -> instrument.contractSize() != null ? instrument.contractSize() : BigDecimal.ONE)
                                                       .orElse(BigDecimal.ONE);
        BigDecimal marginUsed = event.price().multiply(event.quantity()).multiply(contractMultiplier);
        BigDecimal actualFee = isMaker ? event.makerFee() : event.takerFee();
        
        // 用order id 查分錄查出凍結時到底凍結多少錢，再去算要退多少手續費，因為凍結後可能調整費率，導致撮合時收的手續費不一樣
        BigDecimal totalReserved = userJournalRepository.findLatestByReference(
                        userSpot.getAccountId(), asset, ReferenceType.FUNDS_FREEZE_REQUESTED, orderId.toString())
                                                        .map(UserJournal::getAmount)
                                                        .orElseThrow(() -> OpenException.of(AccountErrorCode.FREEZE_ENTRY_NOT_FOUND,
                                                                                            Map.of("orderId", orderId, "userId", userId)));
        BigDecimal totalUsed = marginUsed.add(actualFee);
        if (totalReserved.compareTo(totalUsed) < 0) {
            // 人工排查: 可能是 BUG 預扣扣不夠。
            // 也可能是 預扣後 在成交前，調高了手續費 -> 不跟用戶算這些差額，不扣
            OpenLog.warn(AccountEvent.INSUFFICIENT_RESERVED_BALANCE,
                         "userId", userId,
                         "orderId", orderId,
                         "reserved", totalReserved,
                         "marginUsed", marginUsed,
                         "actualFee", actualFee);
        }
        BigDecimal feeRefund = totalReserved.subtract(totalUsed);
        if(feeRefund.signum() < 0) {
            // 預扣後 在成交前，調高了手續費 -> 不跟用戶算這些差額，不扣
            feeRefund = BigDecimal.ZERO;
        }

        UserAccount userMargin = userAccountRepository.getOrCreate(userId, UserAccountCode.MARGIN, event.instrumentId(), asset);
        UserAccount userFeeExpense = userAccountRepository.getOrCreate(userId, UserAccountCode.FEE_EXPENSE, null, asset);
        PlatformAccount platformAsset = platformAccountRepository.getOrCreate(PlatformAccountCode.HOT_WALLET, asset);
        PlatformAccount platformRevenue = platformAccountRepository.getOrCreate(PlatformAccountCode.TRADING_FEE_REVENUE, asset);
        if (userSpot.getReserved().compareTo(totalReserved) < 0) {
            throw OpenException.of(AccountErrorCode.INSUFFICIENT_RESERVED_BALANCE,
                                   Map.of("userId", userId, "reserved", userSpot.getReserved(), "required", totalReserved));
        }
        UserAccount settledAccount = applyTradeSettlement(userSpot, totalReserved, totalUsed, feeRefund);
        UserAccount updatedMargin = applyUserUpdate(userMargin, Direction.DEBIT, marginUsed);
        UserAccount updatedFeeExpense = applyUserUpdate(userFeeExpense, Direction.DEBIT, actualFee);
        userAccountRepository.updateSelectiveBatch(
                List.of(settledAccount, updatedMargin, updatedFeeExpense),
                List.of(userSpot.safeVersion(), userMargin.safeVersion(), userFeeExpense.safeVersion()),
                "trade-settle");
        PlatformAccount updatedPlatformAsset = applyPlatformUpdate(platformAsset, Direction.DEBIT, actualFee);
        PlatformAccount updatedRevenue = applyPlatformUpdate(platformRevenue, Direction.CREDIT, actualFee);
        platformAccountRepository.updateSelectiveBatch(
                List.of(updatedPlatformAsset, updatedRevenue),
                List.of(platformAsset.safeVersion(), platformRevenue.safeVersion()),
                "trade-settle");
        UserJournal marginJournal = buildUserJournal(
                updatedMargin,
                Direction.DEBIT,
                marginUsed,
                ReferenceType.TRADE_MARGIN_SETTLED,
                refIdWithSide,
                "Margin allocated to isolated account",
                event.executedAt());
        UserJournal marginOutJournal = buildUserJournal(
                settledAccount,
                Direction.CREDIT,
                marginUsed,
                ReferenceType.TRADE_MARGIN_SETTLED,
                refIdWithSide,
                "Margin transferred from spot",
                event.executedAt());
        UserJournal feeJournal = buildUserJournal(
                settledAccount,
                Direction.CREDIT,
                actualFee,
                ReferenceType.TRADE_FEE,
                refIdWithSide,
                "Trading fee deducted",
                event.executedAt());
        UserJournal feeExpenseJournal = buildUserJournal(
                updatedFeeExpense,
                Direction.DEBIT,
                actualFee,
                ReferenceType.TRADE_FEE_EXPENSE,
                refIdWithSide,
                "Trading fee expense",
                event.executedAt());
        if (feeRefund.signum() > 0) {
            UserJournal refundJournal = buildUserJournal(
                    settledAccount,
                    Direction.DEBIT,
                    feeRefund,
                    ReferenceType.TRADE_FEE_REFUND,
                    refIdWithSide,
                    "Maker fee refund",
                    event.executedAt());
            userJournalRepository.insertBatch(List.of(marginJournal, marginOutJournal, feeJournal, feeExpenseJournal, refundJournal));
        } else {
            userJournalRepository.insertBatch(List.of(marginJournal, marginOutJournal, feeJournal, feeExpenseJournal));
        }
        PlatformJournal platformAssetJournal = buildPlatformJournal(
                updatedPlatformAsset,
                Direction.DEBIT,
                actualFee,
                ReferenceType.TRADE_FEE,
                refIdWithSide,
                "Trading fee received",
                event.executedAt());
        PlatformJournal revenueJournal = buildPlatformJournal(
                updatedRevenue,
                Direction.CREDIT,
                actualFee,
                ReferenceType.TRADE_FEE,
                refIdWithSide,
                "Trading fee revenue",
                event.executedAt());
        platformJournalRepository.insertBatch(List.of(platformAssetJournal, revenueJournal));
        tradeSettlementEventPublisher.publishTradeMarginSettled(
                new TradeMarginSettledEvent(
                        event.tradeId(),
                        orderId,
                        userId,
                        event.instrumentId(),
                        asset,
                        isMaker ? event.orderSide() : event.counterpartyOrderSide(),
                        event.price(),
                        event.quantity(),
                        marginUsed,
                        actualFee,
                        feeRefund,
                        event.executedAt(),
                        Instant.now()
                )
        );
    }
}
