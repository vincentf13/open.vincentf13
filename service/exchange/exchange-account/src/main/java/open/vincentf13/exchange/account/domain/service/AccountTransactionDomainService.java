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
import open.vincentf13.exchange.common.sdk.enums.OrderSide;
import open.vincentf13.exchange.common.sdk.enums.PositionIntentType;
import open.vincentf13.exchange.matching.sdk.mq.event.TradeExecutedEvent;
import open.vincentf13.exchange.position.sdk.mq.event.PositionCloseToOpenCompensationEvent;
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
        
        UserAccount updatedUserAsset = userAssetAccount.apply(Direction.DEBIT, request.getAmount());
        UserAccount updatedUserEquity = userEquityAccount.apply(Direction.CREDIT, request.getAmount());
        PlatformAccount updatedPlatformAsset = platformAssetAccount.apply(Direction.DEBIT, request.getAmount());
        PlatformAccount updatedPlatformLiability = platformLiabilityAccount.apply(Direction.CREDIT, request.getAmount());
        
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
        BigDecimal requiredMargin = event.requiredMargin();
        BigDecimal fee = event.fee();
        BigDecimal totalAmount = requiredMargin.add(fee);
        
        UserAccount userSpot = userAccountRepository.getOrCreate(event.userId(), UserAccountCode.SPOT, null, asset);
        assertNoDuplicateJournal(userSpot.getAccountId(), asset, ReferenceType.ORDER_MARGIN_FROZEN, event.orderId().toString());
        
        if (totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw OpenException.of(AccountErrorCode.INVALID_AMOUNT, Map.of("orderId", event.orderId(), "amount", totalAmount));
        }
        if (!userSpot.hasEnoughAvailable(totalAmount)) {
            throw OpenException.of(AccountErrorCode.INSUFFICIENT_FUNDS,
                                   Map.of("userId", event.userId(), "asset", asset, "available", userSpot.getAvailable(), "amount", totalAmount));
        }
        UserAccount frozenAccount = applyUserFreeze(userSpot, totalAmount);
        userAccountRepository.updateSelectiveBatch(
                List.of(frozenAccount),
                List.of(userSpot.safeVersion()),
                "order-freeze");
        UserJournal marginReserveJournal = buildUserJournal(
                frozenAccount,
                Direction.DEBIT,
                requiredMargin,
                ReferenceType.ORDER_MARGIN_FROZEN,
                event.orderId().toString(),
                "Margin reserved for order",
                event.createdAt());
        UserJournal marginAvailableJournal = buildUserJournal(
                frozenAccount,
                Direction.CREDIT,
                requiredMargin,
                ReferenceType.ORDER_MARGIN_FROZEN,
                event.orderId().toString(),
                "Margin moved from available",
                event.createdAt());
        UserJournal feeReserveJournal = buildUserJournal(
                frozenAccount,
                Direction.DEBIT,
                fee,
                ReferenceType.ORDER_FEE_FROZEN,
                event.orderId().toString(),
                "Fee reserved for order",
                event.createdAt());
        UserJournal feeAvailableJournal = buildUserJournal(
                frozenAccount,
                Direction.CREDIT,
                fee,
                ReferenceType.ORDER_FEE_FROZEN,
                event.orderId().toString(),
                "Fee moved from available",
                event.createdAt());
        userJournalRepository.insertBatch(List.of(marginReserveJournal, marginAvailableJournal, feeReserveJournal, feeAvailableJournal));
        return new FundsFreezeResult(asset, totalAmount);
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
        
        UserAccount updatedUserAsset = userAssetAccount.apply(Direction.CREDIT, request.getAmount());
        UserAccount updatedUserEquity = userEquityAccount.apply(Direction.DEBIT, request.getAmount());
        PlatformAccount updatedPlatformAsset = platformAssetAccount.apply(Direction.CREDIT, request.getAmount());
        PlatformAccount updatedPlatformLiability = platformLiabilityAccount.apply(Direction.DEBIT, request.getAmount());
        
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
    

    @Transactional(rollbackFor = Exception.class)
    public void settleTrade(@NotNull @Valid TradeExecutedEvent event,
                            @NotNull Long orderId,
                            @NotNull Long userId,
                            @NotNull OrderSide orderSide,
                            @NotNull PositionIntentType intent,
                            boolean isMaker) {
        AssetSymbol asset = event.quoteAsset();
        UserAccount userSpot = userAccountRepository.getOrCreate(userId, UserAccountCode.SPOT, null, asset);
        
        String refIdWithSide = event.tradeId() + ":" + orderSide.name();
        assertNoDuplicateJournal(userSpot.getAccountId(), asset, ReferenceType.TRADE_MARGIN_SETTLED, refIdWithSide);
        if (intent != PositionIntentType.INCREASE) {
            throw OpenException.of(AccountErrorCode.UNSUPPORTED_ORDER_INTENT,
                                   Map.of("orderId", orderId, "intent", intent));
        }
   
        BigDecimal actualFee = isMaker ? event.makerFee() : event.takerFee();
        
        // 用order id 查分錄查出凍結時到底凍結多少錢，再去算要退多少手續費，因為凍結後可能調整費率，導致撮合時收的手續費不一樣
        BigDecimal marginReserved = userJournalRepository.findLatestByReference(
                        userSpot.getAccountId(), asset, ReferenceType.ORDER_MARGIN_FROZEN, orderId.toString())
                                                        .map(UserJournal::getAmount)
                                                        .orElseThrow(() -> OpenException.of(AccountErrorCode.FREEZE_ENTRY_NOT_FOUND,
                                                                                            Map.of("orderId", orderId, "userId", userId)));
        BigDecimal feeReserved = userJournalRepository.findLatestByReference(
                        userSpot.getAccountId(), asset, ReferenceType.ORDER_FEE_FROZEN, orderId.toString())
                                                    .map(UserJournal::getAmount)
                                                    .orElseThrow(() -> OpenException.of(AccountErrorCode.FREEZE_ENTRY_NOT_FOUND,
                                                                                        Map.of("orderId", orderId, "userId", userId)));
        BigDecimal totalReserved = marginReserved.add(feeReserved);

        BigDecimal feeRefund = feeReserved.subtract(actualFee);
        if(feeRefund.signum() < 0) {
            // 預扣後 在成交前，調高了手續費 -> 不跟用戶算這些差額，不扣
            feeRefund = BigDecimal.ZERO;
        }

        UserAccount userMargin = userAccountRepository.getOrCreate(userId, UserAccountCode.MARGIN, event.instrumentId(), asset);
        UserAccount userFeeExpense = userAccountRepository.getOrCreate(userId, UserAccountCode.FEE_EXPENSE, null, asset);
        UserAccount userFeeEquity = userAccountRepository.getOrCreate(userId, UserAccountCode.FEE_EQUITY, null, asset);
        PlatformAccount platformAsset = platformAccountRepository.getOrCreate(PlatformAccountCode.HOT_WALLET, asset);
        PlatformAccount platformRevenue = platformAccountRepository.getOrCreate(PlatformAccountCode.TRADING_FEE_REVENUE, asset);
        PlatformAccount platformFeeEquity = platformAccountRepository.getOrCreate(PlatformAccountCode.TRADING_FEE_EQUITY, asset);
        if (userSpot.getReserved().compareTo(totalReserved) < 0) {
            throw OpenException.of(AccountErrorCode.INSUFFICIENT_RESERVED_BALANCE,
                                   Map.of("userId", userId, "reserved", userSpot.getReserved(), "required", totalReserved));
        }
        UserAccount settledAccount = userSpot.applyTradeSettlement(totalReserved, feeRefund);
        UserAccount updatedMargin = userMargin.apply(Direction.DEBIT, marginReserved);
        UserAccount updatedFeeExpense = userFeeExpense.apply(Direction.DEBIT, actualFee);
        UserAccount updatedUserFeeEquity = userFeeEquity.applyAllowNegative(Direction.DEBIT, actualFee);
        userAccountRepository.updateSelectiveBatch(
                List.of(settledAccount, updatedMargin, updatedFeeExpense, updatedUserFeeEquity),
                List.of(userSpot.safeVersion(), userMargin.safeVersion(), userFeeExpense.safeVersion(), userFeeEquity.safeVersion()),
                "trade-settle");
        PlatformAccount updatedPlatformAsset = platformAsset.apply(Direction.DEBIT, actualFee);
        PlatformAccount updatedRevenue = platformRevenue.apply(Direction.CREDIT, actualFee);
        PlatformAccount updatedPlatformFeeEquity = platformFeeEquity.apply(Direction.CREDIT, actualFee);
        platformAccountRepository.updateSelectiveBatch(
                List.of(updatedPlatformAsset, updatedRevenue, updatedPlatformFeeEquity),
                List.of(platformAsset.safeVersion(), platformRevenue.safeVersion(), platformFeeEquity.safeVersion()),
                "trade-settle");
        UserJournal marginJournal = buildUserJournal(
                updatedMargin,
                Direction.DEBIT,
                marginReserved,
                ReferenceType.TRADE_MARGIN_SETTLED,
                refIdWithSide,
                "Margin allocated to isolated account",
                event.executedAt());
        UserJournal marginOutJournal = buildUserJournal(
                settledAccount,
                Direction.CREDIT,
                marginReserved,
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
                ReferenceType.TRADE_FEE,
                refIdWithSide,
                "Trading fee expense",
                event.executedAt());
        UserJournal feeEquityJournal = buildUserJournal(
                updatedUserFeeEquity,
                Direction.DEBIT,
                actualFee,
                ReferenceType.TRADE_FEE,
                refIdWithSide,
                "Trading fee equity",
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
            userJournalRepository.insertBatch(List.of(marginJournal, marginOutJournal, feeJournal, feeExpenseJournal, feeEquityJournal, refundJournal));
        } else {
            userJournalRepository.insertBatch(List.of(marginJournal, marginOutJournal, feeJournal, feeExpenseJournal, feeEquityJournal));
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
        PlatformJournal platformFeeEquityJournal = buildPlatformJournal(
                updatedPlatformFeeEquity,
                Direction.CREDIT,
                actualFee,
                ReferenceType.TRADE_FEE,
                refIdWithSide,
                "Trading fee equity",
                event.executedAt());
        platformJournalRepository.insertBatch(List.of(platformAssetJournal, revenueJournal, platformFeeEquityJournal));
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
                        marginReserved,
                        actualFee,
                        event.executedAt(),
                        Instant.now(),
                        false
                )
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public void settleCloseToOpenCompensation(@NotNull @Valid PositionCloseToOpenCompensationEvent event) {
        AssetSymbol asset = event.asset();
        UserAccount userSpot = userAccountRepository.getOrCreate(event.userId(), UserAccountCode.SPOT, null, asset);

        String refIdWithSide = event.tradeId() + ":" + event.side().name();
        assertNoDuplicateJournal(userSpot.getAccountId(), asset, ReferenceType.TRADE_MARGIN_SETTLED, refIdWithSide);

        BigDecimal contractMultiplier = requireContractSize(event.instrumentId());
        BigDecimal marginUsed = event.price().multiply(event.quantity()).multiply(contractMultiplier);
        BigDecimal actualFee = event.feeCharged();
        BigDecimal totalUsed = marginUsed.add(actualFee);

        UserAccount userMargin = userAccountRepository.getOrCreate(event.userId(), UserAccountCode.MARGIN, event.instrumentId(), asset);
        UserAccount userFeeExpense = userAccountRepository.getOrCreate(event.userId(), UserAccountCode.FEE_EXPENSE, null, asset);
        UserAccount userFeeEquity = userAccountRepository.getOrCreate(event.userId(), UserAccountCode.FEE_EQUITY, null, asset);
        PlatformAccount platformAsset = platformAccountRepository.getOrCreate(PlatformAccountCode.HOT_WALLET, asset);
        PlatformAccount platformRevenue = platformAccountRepository.getOrCreate(PlatformAccountCode.TRADING_FEE_REVENUE, asset);
        PlatformAccount platformFeeEquity = platformAccountRepository.getOrCreate(PlatformAccountCode.TRADING_FEE_EQUITY, asset);
        
        UserAccount updatedSpot = userSpot.applyAllowNegative(Direction.CREDIT, totalUsed);
        UserAccount updatedMargin = userMargin.apply(Direction.DEBIT, marginUsed);
        UserAccount updatedFeeExpense = userFeeExpense.apply(Direction.DEBIT, actualFee);
        UserAccount updatedUserFeeEquity = userFeeEquity.applyAllowNegative(Direction.DEBIT, actualFee);
        userAccountRepository.updateSelectiveBatch(
                List.of(updatedSpot, updatedMargin, updatedFeeExpense, updatedUserFeeEquity),
                List.of(userSpot.safeVersion(), userMargin.safeVersion(), userFeeExpense.safeVersion(), userFeeEquity.safeVersion()),
                "close-to-open-compensation");
        PlatformAccount updatedPlatformAsset = platformAsset.apply(Direction.DEBIT, actualFee);
        PlatformAccount updatedRevenue = platformRevenue.apply(Direction.CREDIT, actualFee);
        PlatformAccount updatedPlatformFeeEquity = platformFeeEquity.apply(Direction.CREDIT, actualFee);
        platformAccountRepository.updateSelectiveBatch(
                List.of(updatedPlatformAsset, updatedRevenue, updatedPlatformFeeEquity),
                List.of(platformAsset.safeVersion(), platformRevenue.safeVersion(), platformFeeEquity.safeVersion()),
                "close-to-open-compensation");

        UserJournal marginJournal = buildUserJournal(
                updatedMargin,
                Direction.DEBIT,
                marginUsed,
                ReferenceType.TRADE_MARGIN_SETTLED,
                refIdWithSide,
                "Margin allocated by close-to-open compensation",
                event.executedAt());
        UserJournal marginOutJournal = buildUserJournal(
                updatedSpot,
                Direction.CREDIT,
                marginUsed,
                ReferenceType.TRADE_MARGIN_SETTLED,
                refIdWithSide,
                "Margin transferred from spot (close-to-open compensation)",
                event.executedAt());
        UserJournal feeJournal = buildUserJournal(
                updatedSpot,
                Direction.CREDIT,
                actualFee,
                ReferenceType.TRADE_FEE,
                refIdWithSide,
                "Trading fee deducted (close-to-open compensation)",
                event.executedAt());
        UserJournal feeExpenseJournal = buildUserJournal(
                updatedFeeExpense,
                Direction.DEBIT,
                actualFee,
                ReferenceType.TRADE_FEE,
                refIdWithSide,
                "Trading fee expense",
                event.executedAt());
        UserJournal feeEquityJournal = buildUserJournal(
                updatedUserFeeEquity,
                Direction.DEBIT,
                actualFee,
                ReferenceType.TRADE_FEE,
                refIdWithSide,
                "Trading fee equity",
                event.executedAt());
        userJournalRepository.insertBatch(List.of(marginJournal, marginOutJournal, feeJournal, feeExpenseJournal, feeEquityJournal));

        PlatformJournal platformAssetJournal = buildPlatformJournal(
                updatedPlatformAsset,
                Direction.DEBIT,
                actualFee,
                ReferenceType.TRADE_FEE,
                refIdWithSide,
                "Trading fee received (invalid fill)",
                event.executedAt());
        PlatformJournal revenueJournal = buildPlatformJournal(
                updatedRevenue,
                Direction.CREDIT,
                actualFee,
                ReferenceType.TRADE_FEE,
                refIdWithSide,
                "Trading fee revenue (invalid fill)",
                event.executedAt());
        PlatformJournal platformFeeEquityJournal = buildPlatformJournal(
                updatedPlatformFeeEquity,
                Direction.CREDIT,
                actualFee,
                ReferenceType.TRADE_FEE,
                refIdWithSide,
                "Trading fee equity",
                event.executedAt());
        platformJournalRepository.insertBatch(List.of(platformAssetJournal, revenueJournal, platformFeeEquityJournal));

        if (updatedSpot.getBalance().signum() < 0 || updatedSpot.getAvailable().signum() < 0) {
            OpenLog.warn(AccountEvent.INVALID_FILL_NEGATIVE_BALANCE,
                         "userId", updatedSpot.getUserId(),
                         "asset", updatedSpot.getAsset(),
                         "balance", updatedSpot.getBalance(),
                         "available", updatedSpot.getAvailable(),
                         "tradeId", event.tradeId(),
                         "orderId", event.orderId());
        }

        tradeSettlementEventPublisher.publishTradeMarginSettled(
                new TradeMarginSettledEvent(
                        event.tradeId(),
                        event.orderId(),
                        event.userId(),
                        event.instrumentId(),
                        asset,
                        event.side(),
                        event.price(),
                        event.quantity(),
                        marginUsed,
                        actualFee,
                        event.executedAt(),
                        Instant.now(),
                        true
                )
        );
    }

    private BigDecimal requireContractSize(Long instrumentId) {
        return instrumentCache.get(instrumentId)
                .map(InstrumentSummaryResponse::contractSize)
                .filter(contractSize -> contractSize != null && contractSize.compareTo(BigDecimal.ZERO) > 0)
                .orElseThrow(() -> new IllegalStateException("Instrument cache missing or invalid contractSize for instrumentId=" + instrumentId));
    }
}
