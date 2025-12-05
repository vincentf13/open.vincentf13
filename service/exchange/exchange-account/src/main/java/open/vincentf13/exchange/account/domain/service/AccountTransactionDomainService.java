package open.vincentf13.exchange.account.domain.service;

import com.github.yitter.idgen.DefaultIdGenerator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.domain.model.PlatformAccount;
import open.vincentf13.exchange.account.domain.model.PlatformJournal;
import open.vincentf13.exchange.account.domain.model.UserAccount;
import open.vincentf13.exchange.account.domain.model.UserJournal;
import open.vincentf13.exchange.account.domain.service.result.AccountDepositResult;
import open.vincentf13.exchange.account.domain.service.result.AccountWithdrawalResult;
import open.vincentf13.exchange.account.infra.AccountErrorCode;
import open.vincentf13.exchange.account.infra.persistence.repository.PlatformAccountRepository;
import open.vincentf13.exchange.account.infra.persistence.repository.PlatformJournalRepository;
import open.vincentf13.exchange.account.infra.persistence.repository.UserAccountRepository;
import open.vincentf13.exchange.account.infra.persistence.repository.UserJournalRepository;
import open.vincentf13.exchange.account.sdk.rest.api.dto.AccountDepositRequest;
import open.vincentf13.exchange.account.sdk.rest.api.dto.AccountWithdrawalRequest;
import open.vincentf13.exchange.account.sdk.rest.api.enums.PlatformAccountCode;
import open.vincentf13.exchange.account.sdk.rest.api.enums.UserAccountCode;
import open.vincentf13.exchange.admin.contract.dto.InstrumentSummaryResponse;
import open.vincentf13.exchange.common.sdk.enums.AssetSymbol;
import open.vincentf13.exchange.common.sdk.enums.Direction;
import open.vincentf13.exchange.matching.sdk.mq.event.TradeExecutedEvent;
import open.vincentf13.exchange.order.sdk.rest.dto.OrderResponse;
import open.vincentf13.exchange.order.mq.event.FundsFreezeRequestedEvent;
import open.vincentf13.sdk.core.OpenValidator;
import open.vincentf13.sdk.core.exception.OpenException;
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
    private final DefaultIdGenerator idGenerator;
    
    public record FundsFreezeResult(AssetSymbol asset, BigDecimal amount) {
    }
    
    @Transactional
    public AccountDepositResult deposit(@NotNull @Valid AccountDepositRequest request) {
        
        AssetSymbol asset = UserAccount.normalizeAsset(request.asset());
        Instant eventTime = request.creditedAt() == null ? Instant.now() : request.creditedAt();
        
        UserAccount userAssetAccount = userAccountRepository.getOrCreate(
                request.userId(),
                UserAccountCode.SPOT,
                null,
                asset);
        UserAccount userEquityAccount = userAccountRepository.getOrCreate(
                request.userId(),
                UserAccountCode.SPOT_EQUITY,
                null,
                asset);
        
        PlatformAccount platformAssetAccount = platformAccountRepository.getOrCreate(
                PlatformAccountCode.HOT_WALLET,
                asset);
        PlatformAccount platformLiabilityAccount = platformAccountRepository.getOrCreate(
                PlatformAccountCode.USER_LIABILITY,
                asset);
        
        UserAccount updatedUserAsset = applyUserUpdate(userAssetAccount, Direction.DEBIT, request.amount(), true);
        UserAccount updatedUserEquity = applyUserUpdate(userEquityAccount, Direction.CREDIT, request.amount(), false);
        PlatformAccount updatedPlatformAsset = applyPlatformUpdate(platformAssetAccount, Direction.DEBIT, request.amount());
        PlatformAccount updatedPlatformLiability = applyPlatformUpdate(platformLiabilityAccount, Direction.CREDIT, request.amount());
        
        UserJournal userAssetJournal = buildUserJournal(updatedUserAsset, Direction.DEBIT, request.amount(), "DEPOSIT", request.txId(), "User deposit", eventTime);
        UserJournal userEquityJournal = buildUserJournal(updatedUserEquity, Direction.CREDIT, request.amount(), "DEPOSIT", request.txId(), "User equity increase", eventTime);
        PlatformJournal platformAssetJournal = buildPlatformJournal(updatedPlatformAsset, Direction.DEBIT, request.amount(), "DEPOSIT", request.txId(), "Platform asset increase", eventTime);
        PlatformJournal platformLiabilityJournal = buildPlatformJournal(updatedPlatformLiability, Direction.CREDIT, request.amount(), "DEPOSIT", request.txId(), "Platform liability increase", eventTime);
        
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

    @Transactional
    public FundsFreezeResult freezeForOrder(@NotNull @Valid FundsFreezeRequestedEvent event,
                                            @NotNull InstrumentSummaryResponse instrument) {
        OpenValidator.validateOrThrow(event);
        AssetSymbol asset = resolveAssetForOrder(event, instrument);
        BigDecimal amount = calculateRequiredFunds(event);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw OpenException.of(AccountErrorCode.INVALID_AMOUNT, Map.of("orderId", event.orderId(), "amount", amount));
        }
        UserAccount userSpot = userAccountRepository.getOrCreate(event.userId(), UserAccountCode.SPOT, null, asset);
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
                "FUNDS_FREEZE_REQUESTED",
                event.orderId().toString(),
                "Funds reserved for order",
                event.createdAt());
        UserJournal availableJournal = buildUserJournal(
                frozenAccount,
                Direction.CREDIT,
                amount,
                "FUNDS_FREEZE_REQUESTED",
                event.orderId().toString(),
                "Funds moved from available",
                event.createdAt());
        userJournalRepository.insertBatch(List.of(reserveJournal, availableJournal));
        return new FundsFreezeResult(asset, amount);
    }
    
    @Transactional
    public AccountWithdrawalResult withdraw(@NotNull @Valid AccountWithdrawalRequest request) {
        AssetSymbol asset = UserAccount.normalizeAsset(request.asset());
        Instant eventTime = request.creditedAt() == null ? Instant.now() : request.creditedAt();
        
        UserAccount userAssetAccount = userAccountRepository.getOrCreate(
                request.userId(),
                UserAccountCode.SPOT,
                null,
                asset);
        UserAccount userEquityAccount = userAccountRepository.getOrCreate(
                request.userId(),
                UserAccountCode.SPOT_EQUITY,
                null,
                asset);
        
        if (!userAssetAccount.hasEnoughAvailable(request.amount())) {
            throw OpenException.of(AccountErrorCode.INSUFFICIENT_FUNDS,
                                   Map.of("userId", request.userId(), "asset", asset, "available", userAssetAccount.getAvailable(), "amount", request.amount()));
        }
        
        PlatformAccount platformAssetAccount = platformAccountRepository.getOrCreate(
                PlatformAccountCode.HOT_WALLET,
                asset);
        PlatformAccount platformLiabilityAccount = platformAccountRepository.getOrCreate(
                PlatformAccountCode.USER_LIABILITY,
                asset);
        
        UserAccount updatedUserAsset = applyUserUpdate(userAssetAccount, Direction.CREDIT, request.amount(), true);
        UserAccount updatedUserEquity = applyUserUpdate(userEquityAccount, Direction.DEBIT, request.amount(), false);
        PlatformAccount updatedPlatformAsset = applyPlatformUpdate(platformAssetAccount, Direction.CREDIT, request.amount());
        PlatformAccount updatedPlatformLiability = applyPlatformUpdate(platformLiabilityAccount, Direction.DEBIT, request.amount());
        
        UserJournal userAssetJournal = buildUserJournal(updatedUserAsset, Direction.CREDIT, request.amount(), "WITHDRAWAL", request.txId(), "User withdrawal", eventTime);
        UserJournal userEquityJournal = buildUserJournal(updatedUserEquity, Direction.DEBIT, request.amount(), "WITHDRAWAL", request.txId(), "User equity decrease", eventTime);
        PlatformJournal platformAssetJournal = buildPlatformJournal(updatedPlatformAsset, Direction.CREDIT, request.amount(), "WITHDRAWAL", request.txId(), "Platform payout", eventTime);
        PlatformJournal platformLiabilityJournal = buildPlatformJournal(updatedPlatformLiability, Direction.DEBIT, request.amount(), "WITHDRAWAL", request.txId(), "Platform liability decrease", eventTime);
        
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
                                        BigDecimal amount,
                                        boolean affectAvailable) {
        return current.apply(direction, amount, affectAvailable);
    }
    
    private PlatformAccount applyPlatformUpdate(PlatformAccount current,
                                                Direction direction,
                                                BigDecimal amount) {
        return current.apply(direction, amount);
    }
    
    private UserJournal buildUserJournal(UserAccount account,
                                         Direction direction,
                                         BigDecimal amount,
                                         String referenceType,
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
                                                 String referenceType,
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

    private AssetSymbol resolveAssetForOrder(FundsFreezeRequestedEvent event,
                                             InstrumentSummaryResponse instrument) {
        return switch (event.side()) {
            case BUY -> AssetSymbol.fromValue(instrument.quoteAsset());
            case SELL -> AssetSymbol.fromValue(instrument.baseAsset());
        };
    }

    private BigDecimal calculateRequiredFunds(FundsFreezeRequestedEvent event) {
        if (event.price() == null || event.quantity() == null) {
            throw OpenException.of(AccountErrorCode.INVALID_AMOUNT, Map.of("orderId", event.orderId(), "message", "price or quantity missing"));
        }
        return event.price().multiply(event.quantity());
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
                          .updatedAt(Instant.now())
                          .build();
    }

    @Transactional
    public void settleTrade(@NotNull @Valid TradeExecutedEvent event,
                            @NotNull @Valid OrderResponse order,
                            boolean isMaker) {
        throw OpenException.of(AccountErrorCode.INVALID_EVENT,
                               Map.of("tradeId", event.tradeId(), "orderId", order.orderId(), "message", "Trade settlement not implemented for new account schema yet"));
    }
}
