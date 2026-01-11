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
import open.vincentf13.exchange.common.sdk.constants.ValidationConstant;
import open.vincentf13.exchange.common.sdk.enums.AssetSymbol;
import open.vincentf13.exchange.common.sdk.enums.Direction;
import open.vincentf13.exchange.common.sdk.enums.OrderSide;
import open.vincentf13.exchange.common.sdk.enums.PositionIntentType;
import open.vincentf13.exchange.matching.sdk.mq.event.TradeExecutedEvent;
import open.vincentf13.exchange.position.sdk.mq.event.PositionCloseToOpenCompensationEvent;
import open.vincentf13.exchange.position.sdk.mq.event.PositionOpenToCloseCompensationEvent;
import open.vincentf13.exchange.order.mq.event.FundsFreezeRequestedEvent;
import open.vincentf13.sdk.core.validator.OpenValidator;
import open.vincentf13.sdk.core.exception.OpenException;
import open.vincentf13.sdk.core.log.OpenLog;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
        
        Long accountId = userAssetAccount.getAccountId();
        String referenceId = request.getTxId();
        boolean exists = userJournalRepository.findLatestByReference(accountId, asset, ReferenceType.DEPOSIT, referenceId).isPresent();
        if (exists) {
            throw OpenException.of(AccountErrorCode.DUPLICATE_REQUEST,
                                   Map.of("accountId", accountId, "asset", asset, "referenceType", ReferenceType.DEPOSIT, "referenceId", referenceId));
        }
        
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
        
        int uSeq = 1;
        var userAssetResult = userAssetAccount.applyWithJournal(Direction.DEBIT, request.getAmount(), idGenerator.newLong(), ReferenceType.DEPOSIT, request.getTxId(), uSeq++, "User deposit", eventTime);
        UserAccount updatedUserAsset = userAssetResult.account();
        UserJournal userAssetJournal = userAssetResult.journal();

        var userEquityResult = userEquityAccount.applyWithJournal(Direction.CREDIT, request.getAmount(), idGenerator.newLong(), ReferenceType.DEPOSIT, request.getTxId(), uSeq++, "User equity increase", eventTime);
        UserAccount updatedUserEquity = userEquityResult.account();
        UserJournal userEquityJournal = userEquityResult.journal();
        
        int pSeq = 1;
        var platformAssetResult = platformAssetAccount.applyWithJournal(Direction.DEBIT, request.getAmount(), idGenerator.newLong(), ReferenceType.DEPOSIT, request.getTxId(), pSeq++, "Platform asset increase", eventTime);
        PlatformAccount updatedPlatformAsset = platformAssetResult.account();
        PlatformJournal platformAssetJournal = platformAssetResult.journal();

        var platformLiabilityResult = platformLiabilityAccount.applyWithJournal(Direction.CREDIT, request.getAmount(), idGenerator.newLong(), ReferenceType.DEPOSIT, request.getTxId(), pSeq++, "Platform liability increase", eventTime);
        PlatformAccount updatedPlatformLiability = platformLiabilityResult.account();
        PlatformJournal platformLiabilityJournal = platformLiabilityResult.journal();
        
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
        Long accountId = userSpot.getAccountId();
        String referenceId = event.orderId().toString();
        boolean exists = userJournalRepository.findLatestByReference(accountId, asset, ReferenceType.ORDER_MARGIN_FROZEN, referenceId).isPresent();
        if (exists) {
            throw OpenException.of(AccountErrorCode.DUPLICATE_REQUEST,
                                   Map.of("accountId", accountId, "asset", asset, "referenceType", ReferenceType.ORDER_MARGIN_FROZEN, "referenceId", referenceId));
        }
        
        if (totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw OpenException.of(AccountErrorCode.INVALID_AMOUNT, Map.of("orderId", event.orderId(), "amount", totalAmount));
        }
        if (!userSpot.hasEnoughAvailable(totalAmount)) {
            throw OpenException.of(AccountErrorCode.INSUFFICIENT_FUNDS,
                                   Map.of("userId", event.userId(), "asset", asset, "available", userSpot.getAvailable(), "amount", totalAmount));
        }

        int uSeq = 1;
        var marginFrozenResult = userSpot.freezeWithJournal(requiredMargin, idGenerator.newLong(), ReferenceType.ORDER_MARGIN_FROZEN, event.orderId().toString(), uSeq++, "Margin reserved for order", event.createdAt());
        UserAccount marginFrozenAccount = marginFrozenResult.account();
        UserJournal marginReserveJournal = marginFrozenResult.journal();

        var feeFrozenResult = marginFrozenAccount.freezeWithJournal(fee, idGenerator.newLong(), ReferenceType.ORDER_FEE_FROZEN, event.orderId().toString(), uSeq++, "Fee reserved for order", event.createdAt());
        UserAccount feeFrozenAccount = feeFrozenResult.account();
        UserJournal feeReserveJournal = feeFrozenResult.journal();

        userAccountRepository.updateSelectiveBatch(
                List.of(feeFrozenAccount),
                List.of(userSpot.safeVersion()),
                "order-freeze");
        
        userJournalRepository.insertBatch(List.of(marginReserveJournal, feeReserveJournal));
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
        
        Long accountId = userAssetAccount.getAccountId();
        String referenceId = request.getTxId();
        boolean exists = userJournalRepository.findLatestByReference(accountId, asset, ReferenceType.WITHDRAWAL, referenceId).isPresent();
        if (exists) {
            throw OpenException.of(AccountErrorCode.DUPLICATE_REQUEST,
                                   Map.of("accountId", accountId, "asset", asset, "referenceType", ReferenceType.WITHDRAWAL, "referenceId", referenceId));
        }
        
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
        
        int uSeq = 1;
        var userAssetResult = userAssetAccount.applyWithJournal(Direction.CREDIT, request.getAmount(), idGenerator.newLong(), ReferenceType.WITHDRAWAL, request.getTxId(), uSeq++, "User withdrawal", eventTime);
        UserAccount updatedUserAsset = userAssetResult.account();
        UserJournal userAssetJournal = userAssetResult.journal();

        var userEquityResult = userEquityAccount.applyWithJournal(Direction.DEBIT, request.getAmount(), idGenerator.newLong(), ReferenceType.WITHDRAWAL, request.getTxId(), uSeq++, "User equity decrease", eventTime);
        UserAccount updatedUserEquity = userEquityResult.account();
        UserJournal userEquityJournal = userEquityResult.journal();
        
        int pSeq = 1;
        var platformAssetResult = platformAssetAccount.applyWithJournal(Direction.CREDIT, request.getAmount(), idGenerator.newLong(), ReferenceType.WITHDRAWAL, request.getTxId(), pSeq++, "Platform payout", eventTime);
        PlatformAccount updatedPlatformAsset = platformAssetResult.account();
        PlatformJournal platformAssetJournal = platformAssetResult.journal();

        var platformLiabilityResult = platformLiabilityAccount.applyWithJournal(Direction.DEBIT, request.getAmount(), idGenerator.newLong(), ReferenceType.WITHDRAWAL, request.getTxId(), pSeq++, "Platform liability decrease", eventTime);
        PlatformAccount updatedPlatformLiability = platformLiabilityResult.account();
        PlatformJournal platformLiabilityJournal = platformLiabilityResult.journal();
        
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
        Long accountId = userSpot.getAccountId();
        boolean exists = userJournalRepository.findLatestByReference(accountId, asset, ReferenceType.TRADE_MARGIN_SETTLED, refIdWithSide).isPresent();
        if (exists) {
            throw OpenException.of(AccountErrorCode.DUPLICATE_REQUEST,
                                   Map.of("accountId", accountId, "asset", asset, "referenceType", ReferenceType.TRADE_MARGIN_SETTLED, "referenceId", refIdWithSide));
        }
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
        
        // 計算成交比例
        BigDecimal tradeQuantity = event.quantity();
        if (tradeQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw OpenException.of(AccountErrorCode.INVALID_EVENT, Map.of("orderId", orderId, "userId", userId));
        }
        BigDecimal orderQuantity = isMaker ? event.orderQuantity() : event.counterpartyOrderQuantity();
        BigDecimal orderFilledQuantity = isMaker ? event.orderFilledQuantity() : event.counterpartyOrderFilledQuantity();
        if (orderQuantity == null || orderFilledQuantity == null || orderQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw OpenException.of(AccountErrorCode.INVALID_EVENT, Map.of("orderId", orderId, "userId", userId));
        }
        if (orderFilledQuantity.compareTo(tradeQuantity) < 0) {
            throw OpenException.of(AccountErrorCode.INVALID_EVENT, Map.of("orderId", orderId, "userId", userId));
        }
        BigDecimal previousFilled = orderFilledQuantity.subtract(tradeQuantity);
        if (previousFilled.signum() < 0) {
            throw OpenException.of(AccountErrorCode.INVALID_EVENT,
                                   Map.of("orderId", orderId,
                                          "userId", userId,
                                          "orderFilledQuantity", orderFilledQuantity,
                                          "tradeQuantity", tradeQuantity));
        }
        BigDecimal marginPortion;
        BigDecimal feeReservedPortion;
        boolean isFinalFill = orderFilledQuantity.compareTo(orderQuantity) >= 0;
        // 最後一次要把所有剩餘算入
        if (isFinalFill) {
            BigDecimal marginUsed = marginReserved.multiply(previousFilled)
                                                  .divide(orderQuantity, ValidationConstant.Names.COMMON_SCALE, RoundingMode.HALF_UP);
            BigDecimal feeUsed = feeReserved.multiply(previousFilled)
                                            .divide(orderQuantity, ValidationConstant.Names.COMMON_SCALE, RoundingMode.HALF_UP);
            marginPortion = marginReserved.subtract(marginUsed);
            feeReservedPortion = feeReserved.subtract(feeUsed);
        } else {
            marginPortion = marginReserved.multiply(tradeQuantity)
                                          .divide(orderQuantity, ValidationConstant.Names.COMMON_SCALE, RoundingMode.HALF_UP);
            feeReservedPortion = feeReserved.multiply(tradeQuantity)
                                            .divide(orderQuantity, ValidationConstant.Names.COMMON_SCALE, RoundingMode.HALF_UP);
        }
        if (marginPortion.signum() < 0) {
            throw OpenException.of(AccountErrorCode.INVALID_EVENT,
                                   Map.of("orderId", orderId,
                                          "userId", userId,
                                          "marginReserved", marginReserved,
                                          "marginPortion", marginPortion));
        }
        if (feeReservedPortion.signum() < 0) {
            throw OpenException.of(AccountErrorCode.INVALID_EVENT,
                                   Map.of("orderId", orderId,
                                          "userId", userId,
                                          "feeReserved", feeReserved,
                                          "feeReservedPortion", feeReservedPortion));
        }
        BigDecimal totalReservedUsed = marginPortion.add(feeReservedPortion);

        BigDecimal feeRefund = feeReservedPortion.subtract(actualFee);
        if (feeRefund.signum() < 0) {
            // 預扣後 在成交前，調高了手續費 -> 不跟用戶算這些差額，不扣
            feeRefund = BigDecimal.ZERO;
        }

        UserAccount userMargin = userAccountRepository.getOrCreate(userId, UserAccountCode.MARGIN, event.instrumentId(), asset);
        UserAccount userFeeExpense = userAccountRepository.getOrCreate(userId, UserAccountCode.FEE_EXPENSE, null, asset);
        UserAccount userFeeEquity = userAccountRepository.getOrCreate(userId, UserAccountCode.FEE_EQUITY, null, asset);
        PlatformAccount platformLiability = platformAccountRepository.getOrCreate(PlatformAccountCode.USER_LIABILITY, asset);
        PlatformAccount platformRevenue = platformAccountRepository.getOrCreate(PlatformAccountCode.TRADING_FEE_REVENUE, asset);
        PlatformAccount platformFeeEquity = platformAccountRepository.getOrCreate(PlatformAccountCode.TRADING_FEE_EQUITY, asset);
        if (userSpot.getReserved().compareTo(totalReservedUsed) < 0) {
            throw OpenException.of(AccountErrorCode.INSUFFICIENT_RESERVED_BALANCE,
                                   Map.of("userId", userId, "reserved", userSpot.getReserved(), "required", totalReservedUsed));
        }

        List<UserJournal> journals = new java.util.ArrayList<>(6);
        int uSeq = 1;

        // 1. Move Margin from Reserved (Spot) to Margin Account
        var marginOutResult = userSpot.applyReservedOutWithJournal(marginPortion, idGenerator.newLong(), ReferenceType.TRADE_MARGIN_SETTLED, refIdWithSide, uSeq++, "Margin transferred from spot", event.executedAt());
        UserAccount spotAfterMargin = marginOutResult.account();
        journals.add(marginOutResult.journal());

        var marginInResult = userMargin.applyWithJournal(Direction.DEBIT, marginPortion, idGenerator.newLong(), ReferenceType.TRADE_MARGIN_SETTLED, refIdWithSide, uSeq++, "Margin allocated to isolated account", event.executedAt());
        UserAccount updatedMargin = marginInResult.account();
        journals.add(marginInResult.journal());

        // 2. Refund excess fee reserved
        UserAccount spotAfterFeeRefund = spotAfterMargin;
        if (feeRefund.signum() > 0) {
            var feeRefundResult = spotAfterMargin.refundReservedWithJournal(feeRefund, idGenerator.newLong(), ReferenceType.TRADE_FEE_REFUND, refIdWithSide, uSeq++, "Trading fee refund", event.executedAt());
            spotAfterFeeRefund = feeRefundResult.account();
            journals.add(feeRefundResult.journal());
        }

        // 3. Pay actual fee (if any) from RESERVED
        UserAccount settledAccount = spotAfterFeeRefund;
        if (actualFee.signum() > 0) {
            var feePaymentResult = spotAfterFeeRefund.applyReservedOutWithJournal(actualFee, idGenerator.newLong(), ReferenceType.TRADE_FEE, refIdWithSide, uSeq++, "Trading fee deducted", event.executedAt());
            settledAccount = feePaymentResult.account();
            journals.add(feePaymentResult.journal());
        }

        var feeExpenseResult = userFeeExpense.applyWithJournal(Direction.DEBIT, actualFee, idGenerator.newLong(), ReferenceType.TRADE_FEE, refIdWithSide, uSeq++, "Trading fee expense", event.executedAt());
        UserAccount updatedFeeExpense = feeExpenseResult.account();
        journals.add(feeExpenseResult.journal());

        var feeEquityResult = userFeeEquity.applyAllowNegativeWithJournal(Direction.DEBIT, actualFee, idGenerator.newLong(), ReferenceType.TRADE_FEE, refIdWithSide, uSeq++, "Trading fee equity", event.executedAt());
        UserAccount updatedUserFeeEquity = feeEquityResult.account();
        journals.add(feeEquityResult.journal());
        
        userAccountRepository.updateSelectiveBatch(
                List.of(settledAccount, updatedMargin, updatedFeeExpense, updatedUserFeeEquity),
                List.of(userSpot.safeVersion(), userMargin.safeVersion(), userFeeExpense.safeVersion(), userFeeEquity.safeVersion()),
                "trade-settle");
        
        int pSeq = 1;
        var platformLiabilityResult = platformLiability.applyWithJournal(Direction.DEBIT, actualFee, idGenerator.newLong(), ReferenceType.TRADE_FEE, refIdWithSide, pSeq++, "User liability decreased by trading fee", event.executedAt());
        PlatformAccount updatedPlatformLiability = platformLiabilityResult.account();
        PlatformJournal platformLiabilityJournal = platformLiabilityResult.journal();

        var revenueResult = platformRevenue.applyWithJournal(Direction.CREDIT, actualFee, idGenerator.newLong(), ReferenceType.TRADE_FEE, refIdWithSide, pSeq++, "Trading fee revenue", event.executedAt());
        PlatformAccount updatedRevenue = revenueResult.account();
        PlatformJournal revenueJournal = revenueResult.journal();

        var platformFeeEquityResult = platformFeeEquity.applyWithJournal(Direction.CREDIT, actualFee, idGenerator.newLong(), ReferenceType.TRADE_FEE, refIdWithSide, pSeq++, "Trading fee equity", event.executedAt());
        PlatformAccount updatedPlatformFeeEquity = platformFeeEquityResult.account();
        PlatformJournal platformFeeEquityJournal = platformFeeEquityResult.journal();
        
        platformAccountRepository.updateSelectiveBatch(
                List.of(updatedPlatformLiability, updatedRevenue, updatedPlatformFeeEquity),
                List.of(platformLiability.safeVersion(), platformRevenue.safeVersion(), platformFeeEquity.safeVersion()),
                "trade-settle");
        
        userJournalRepository.insertBatch(journals);
        platformJournalRepository.insertBatch(List.of(platformLiabilityJournal, revenueJournal, platformFeeEquityJournal));
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
                        marginPortion,
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
        Long accountId = userSpot.getAccountId();
        boolean exists = userJournalRepository.findLatestByReference(accountId, asset, ReferenceType.TRADE_MARGIN_SETTLED, refIdWithSide).isPresent();
        if (exists) {
            throw OpenException.of(AccountErrorCode.DUPLICATE_REQUEST,
                                   Map.of("accountId", accountId, "asset", asset, "referenceType", ReferenceType.TRADE_MARGIN_SETTLED, "referenceId", refIdWithSide));
        }
        
        BigDecimal contractMultiplier = requireContractSize(event.instrumentId());
        BigDecimal marginUsed = event.price().multiply(event.quantity()).multiply(contractMultiplier);
        BigDecimal actualFee = event.feeCharged();

        UserAccount userMargin = userAccountRepository.getOrCreate(event.userId(), UserAccountCode.MARGIN, event.instrumentId(), asset);
        UserAccount userFeeExpense = userAccountRepository.getOrCreate(event.userId(), UserAccountCode.FEE_EXPENSE, null, asset);
        UserAccount userFeeEquity = userAccountRepository.getOrCreate(event.userId(), UserAccountCode.FEE_EQUITY, null, asset);
        PlatformAccount platformLiability = platformAccountRepository.getOrCreate(PlatformAccountCode.USER_LIABILITY, asset);
        PlatformAccount platformRevenue = platformAccountRepository.getOrCreate(PlatformAccountCode.TRADING_FEE_REVENUE, asset);
        PlatformAccount platformFeeEquity = platformAccountRepository.getOrCreate(PlatformAccountCode.TRADING_FEE_EQUITY, asset);
        
        int uSeq = 1;
        var marginResult = userMargin.applyWithJournal(Direction.DEBIT, marginUsed, idGenerator.newLong(), ReferenceType.TRADE_MARGIN_SETTLED, refIdWithSide, uSeq++, "Margin allocated by close-to-open compensation", event.executedAt());
        UserAccount updatedMargin = marginResult.account();
        UserJournal marginJournal = marginResult.journal();

        var spotMarginResult = userSpot.applyAllowNegativeWithJournal(Direction.CREDIT, marginUsed, idGenerator.newLong(), ReferenceType.TRADE_MARGIN_SETTLED, refIdWithSide, uSeq++, "Margin transferred from spot (close-to-open compensation)", event.executedAt());
        UserAccount spotAfterMargin = spotMarginResult.account();
        UserJournal marginOutJournal = spotMarginResult.journal();

        var spotFeeResult = spotAfterMargin.applyAllowNegativeWithJournal(Direction.CREDIT, actualFee, idGenerator.newLong(), ReferenceType.TRADE_FEE, refIdWithSide, uSeq++, "Trading fee deducted (close-to-open compensation)", event.executedAt());
        UserAccount updatedSpot = spotFeeResult.account();
        UserJournal feeJournal = spotFeeResult.journal();

        var feeExpenseResult = userFeeExpense.applyWithJournal(Direction.DEBIT, actualFee, idGenerator.newLong(), ReferenceType.TRADE_FEE, refIdWithSide, uSeq++, "Trading fee expense", event.executedAt());
        UserAccount updatedFeeExpense = feeExpenseResult.account();
        UserJournal feeExpenseJournal = feeExpenseResult.journal();

        var feeEquityResult = userFeeEquity.applyAllowNegativeWithJournal(Direction.DEBIT, actualFee, idGenerator.newLong(), ReferenceType.TRADE_FEE, refIdWithSide, uSeq++, "Trading fee equity", event.executedAt());
        UserAccount updatedUserFeeEquity = feeEquityResult.account();
        UserJournal feeEquityJournal = feeEquityResult.journal();

        userAccountRepository.updateSelectiveBatch(
                List.of(updatedSpot, updatedMargin, updatedFeeExpense, updatedUserFeeEquity),
                List.of(userSpot.safeVersion(), userMargin.safeVersion(), userFeeExpense.safeVersion(), userFeeEquity.safeVersion()),
                "close-to-open-compensation");
        
        int pSeq = 1;
        var platformLiabilityResult = platformLiability.applyWithJournal(Direction.DEBIT, actualFee, idGenerator.newLong(), ReferenceType.TRADE_FEE, refIdWithSide, pSeq++, "User liability decreased by trading fee (invalid fill)", event.executedAt());
        PlatformAccount updatedPlatformLiability = platformLiabilityResult.account();
        PlatformJournal platformLiabilityJournal = platformLiabilityResult.journal();

        var revenueResult = platformRevenue.applyWithJournal(Direction.CREDIT, actualFee, idGenerator.newLong(), ReferenceType.TRADE_FEE, refIdWithSide, pSeq++, "Trading fee revenue (invalid fill)", event.executedAt());
        PlatformAccount updatedRevenue = revenueResult.account();
        PlatformJournal revenueJournal = revenueResult.journal();

        var platformFeeEquityResult = platformFeeEquity.applyWithJournal(Direction.CREDIT, actualFee, idGenerator.newLong(), ReferenceType.TRADE_FEE, refIdWithSide, pSeq++, "Trading fee equity", event.executedAt());
        PlatformAccount updatedPlatformFeeEquity = platformFeeEquityResult.account();
        PlatformJournal platformFeeEquityJournal = platformFeeEquityResult.journal();

        platformAccountRepository.updateSelectiveBatch(
                List.of(updatedPlatformLiability, updatedRevenue, updatedPlatformFeeEquity),
                List.of(platformLiability.safeVersion(), platformRevenue.safeVersion(), platformFeeEquity.safeVersion()),
                "close-to-open-compensation");

        userJournalRepository.insertBatch(List.of(marginJournal, marginOutJournal, feeJournal, feeExpenseJournal, feeEquityJournal));
        platformJournalRepository.insertBatch(List.of(platformLiabilityJournal, revenueJournal, platformFeeEquityJournal));

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

    @Transactional(rollbackFor = Exception.class)
    public void settleOpenToCloseCompensation(@NotNull @Valid PositionOpenToCloseCompensationEvent event) {
        AssetSymbol asset = event.asset();
        UserAccount userSpot = userAccountRepository.getOrCreate(event.userId(), UserAccountCode.SPOT, null, asset);

        String refIdWithSide = event.tradeId() + ":" + event.side().name() + ":FLIP";
        Long accountId = userSpot.getAccountId();
        boolean exists = userJournalRepository.findLatestByReference(accountId, asset, ReferenceType.TRADE_MARGIN_SETTLED, refIdWithSide).isPresent();
        if (exists) {
            throw OpenException.of(AccountErrorCode.DUPLICATE_REQUEST,
                                   Map.of("accountId", accountId, "asset", asset, "referenceType", ReferenceType.TRADE_MARGIN_SETTLED, "referenceId", refIdWithSide));
        }
        
        BigDecimal contractMultiplier = requireContractSize(event.instrumentId());
        BigDecimal marginToRefund = event.price().multiply(event.quantity()).multiply(contractMultiplier);
        BigDecimal feeToRefund = event.feeCharged();

        UserAccount userMargin = userAccountRepository.getOrCreate(event.userId(), UserAccountCode.MARGIN, event.instrumentId(), asset);
        UserAccount userFeeExpense = userAccountRepository.getOrCreate(event.userId(), UserAccountCode.FEE_EXPENSE, null, asset);
        UserAccount userFeeEquity = userAccountRepository.getOrCreate(event.userId(), UserAccountCode.FEE_EQUITY, null, asset);
        PlatformAccount platformLiability = platformAccountRepository.getOrCreate(PlatformAccountCode.USER_LIABILITY, asset);
        PlatformAccount platformRevenue = platformAccountRepository.getOrCreate(PlatformAccountCode.TRADING_FEE_REVENUE, asset);
        PlatformAccount platformFeeEquity = platformAccountRepository.getOrCreate(PlatformAccountCode.TRADING_FEE_EQUITY, asset);

        int uSeq = 1;
        // Refund Margin: Debit Margin (Decrease) -> Credit Spot (Increase)
        // Correction: Debit Spot (Increase)
        var marginResult = userMargin.applyWithJournal(Direction.CREDIT, marginToRefund, idGenerator.newLong(), ReferenceType.TRADE_MARGIN_SETTLED, refIdWithSide, uSeq++, "Margin refunded by open-to-close compensation", event.executedAt());
        UserAccount updatedMargin = marginResult.account();
        UserJournal marginJournal = marginResult.journal();

        var spotMarginResult = userSpot.applyWithJournal(Direction.DEBIT, marginToRefund, idGenerator.newLong(), ReferenceType.TRADE_MARGIN_SETTLED, refIdWithSide, uSeq++, "Margin returned to spot (open-to-close compensation)", event.executedAt());
        UserAccount spotAfterMargin = spotMarginResult.account();
        UserJournal marginOutJournal = spotMarginResult.journal();

        // Refund Fee: Credit Fee Expense (Decrease Expense) -> Debit Spot (Increase)
        var feeExpenseResult = userFeeExpense.applyWithJournal(Direction.CREDIT, feeToRefund, idGenerator.newLong(), ReferenceType.TRADE_FEE_REFUND, refIdWithSide, uSeq++, "Trading fee expense reversed", event.executedAt());
        UserAccount updatedFeeExpense = feeExpenseResult.account();
        UserJournal feeExpenseJournal = feeExpenseResult.journal();

        var feeEquityResult = userFeeEquity.applyAllowNegativeWithJournal(Direction.CREDIT, feeToRefund, idGenerator.newLong(), ReferenceType.TRADE_FEE_REFUND, refIdWithSide, uSeq++, "Trading fee equity reversed", event.executedAt());
        UserAccount updatedUserFeeEquity = feeEquityResult.account();
        UserJournal feeEquityJournal = feeEquityResult.journal();

        var spotFeeResult = spotAfterMargin.applyWithJournal(Direction.DEBIT, feeToRefund, idGenerator.newLong(), ReferenceType.TRADE_FEE_REFUND, refIdWithSide, uSeq++, "Trading fee refunded (open-to-close compensation)", event.executedAt());
        UserAccount updatedSpot = spotFeeResult.account();
        UserJournal feeJournal = spotFeeResult.journal();

        userAccountRepository.updateSelectiveBatch(
                List.of(updatedSpot, updatedMargin, updatedFeeExpense, updatedUserFeeEquity),
                List.of(userSpot.safeVersion(), userMargin.safeVersion(), userFeeExpense.safeVersion(), userFeeEquity.safeVersion()),
                "open-to-close-compensation");

        // Reverse Platform Fee: Debit Revenue -> Credit Liability
        int pSeq = 1;
        var platformLiabilityResult = platformLiability.applyWithJournal(Direction.CREDIT, feeToRefund, idGenerator.newLong(), ReferenceType.TRADE_FEE_REFUND, refIdWithSide, pSeq++, "User liability increased (fee refund)", event.executedAt());
        PlatformAccount updatedPlatformLiability = platformLiabilityResult.account();
        PlatformJournal platformLiabilityJournal = platformLiabilityResult.journal();

        var revenueResult = platformRevenue.applyWithJournal(Direction.DEBIT, feeToRefund, idGenerator.newLong(), ReferenceType.TRADE_FEE_REFUND, refIdWithSide, pSeq++, "Trading fee revenue reversed", event.executedAt());
        PlatformAccount updatedRevenue = revenueResult.account();
        PlatformJournal revenueJournal = revenueResult.journal();

        var platformFeeEquityResult = platformFeeEquity.applyWithJournal(Direction.DEBIT, feeToRefund, idGenerator.newLong(), ReferenceType.TRADE_FEE_REFUND, refIdWithSide, pSeq++, "Trading fee equity reversed", event.executedAt());
        PlatformAccount updatedPlatformFeeEquity = platformFeeEquityResult.account();
        PlatformJournal platformFeeEquityJournal = platformFeeEquityResult.journal();

        platformAccountRepository.updateSelectiveBatch(
                List.of(updatedPlatformLiability, updatedRevenue, updatedPlatformFeeEquity),
                List.of(platformLiability.safeVersion(), platformRevenue.safeVersion(), platformFeeEquity.safeVersion()),
                "open-to-close-compensation");

        userJournalRepository.insertBatch(List.of(marginJournal, marginOutJournal, feeJournal, feeExpenseJournal, feeEquityJournal));
        platformJournalRepository.insertBatch(List.of(platformLiabilityJournal, revenueJournal, platformFeeEquityJournal));
    }
    
    private BigDecimal requireContractSize(Long instrumentId) {
        return instrumentCache.get(instrumentId)
                .map(InstrumentSummaryResponse::contractSize)
                .filter(contractSize -> contractSize != null && contractSize.compareTo(BigDecimal.ZERO) > 0)
                .orElseThrow(() -> new IllegalStateException("Instrument cache missing or invalid contractSize for instrumentId=" + instrumentId));
    }

}