package open.vincentf13.exchange.account.domain.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.github.yitter.idgen.DefaultIdGenerator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.domain.model.*;
import open.vincentf13.exchange.account.domain.model.transaction.AccountDepositResult;
import open.vincentf13.exchange.account.domain.model.transaction.AccountWithdrawalResult;
import open.vincentf13.exchange.account.infra.AccountErrorCode;
import open.vincentf13.exchange.account.infra.persistence.po.PlatformAccountPO;
import open.vincentf13.exchange.account.infra.persistence.po.UserAccountPO;
import open.vincentf13.exchange.account.infra.persistence.repository.PlatformAccountRepository;
import open.vincentf13.exchange.account.infra.persistence.repository.PlatformJournalRepository;
import open.vincentf13.exchange.account.infra.persistence.repository.UserAccountRepository;
import open.vincentf13.exchange.account.infra.persistence.repository.UserJournalRepository;
import open.vincentf13.exchange.account.sdk.rest.api.enums.AccountCategory;
import open.vincentf13.exchange.account.sdk.rest.api.enums.PlatformAccountCode;
import open.vincentf13.exchange.account.sdk.rest.api.enums.UserAccountCode;
import open.vincentf13.exchange.account.sdk.rest.api.dto.AccountDepositRequest;
import open.vincentf13.exchange.account.sdk.rest.api.dto.AccountWithdrawalRequest;
import open.vincentf13.exchange.common.sdk.enums.AssetSymbol;
import open.vincentf13.exchange.common.sdk.enums.Direction;
import open.vincentf13.exchange.matching.sdk.mq.event.TradeExecutedEvent;
import open.vincentf13.exchange.order.sdk.rest.dto.OrderResponse;
import open.vincentf13.sdk.core.OpenValidator;
import open.vincentf13.sdk.core.exception.OpenException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.Instant;
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
    
    @Transactional
    public AccountDepositResult deposit(@NotNull @Valid AccountDepositRequest request) {
        OpenValidator.validateOrThrow(request);
        AssetSymbol asset = UserAccount.normalizeAsset(request.asset());
        Instant eventTime = request.creditedAt() == null ? Instant.now() : request.creditedAt();
        
        UserAccount userAssetAccount = userAccountRepository.getOrCreate(
                request.userId(),
                UserAccountCode.SPOT,
                UserAccountCode.SPOT.getDisplayName(),
                null,
                AccountCategory.ASSET,
                asset);
        UserAccount userEquityAccount = userAccountRepository.getOrCreate(
                request.userId(),
                UserAccountCode.SPOT_EQUITY,
                UserAccountCode.SPOT_EQUITY.getDisplayName(),
                null,
                AccountCategory.EQUITY,
                asset);
        
        PlatformAccount platformAssetAccount = platformAccountRepository.getOrCreate(
                PlatformAccountCode.HOT_WALLET,
                PlatformAccountCode.HOT_WALLET.getDisplayName(),
                AccountCategory.ASSET,
                asset);
        PlatformAccount platformLiabilityAccount = platformAccountRepository.getOrCreate(
                PlatformAccountCode.USER_LIABILITY,
                PlatformAccountCode.USER_LIABILITY.getDisplayName(),
                AccountCategory.LIABILITY,
                asset);
        
        UserAccount updatedUserAsset = applyUserUpdate(userAssetAccount, Direction.DEBIT, request.amount(), true, "deposit");
        UserAccount updatedUserEquity = applyUserUpdate(userEquityAccount, Direction.CREDIT, request.amount(), false, "deposit");
        PlatformAccount updatedPlatformAsset = applyPlatformUpdate(platformAssetAccount, Direction.DEBIT, request.amount(), "deposit");
        PlatformAccount updatedPlatformLiability = applyPlatformUpdate(platformLiabilityAccount, Direction.CREDIT, request.amount(), "deposit");
        
        UserJournal userAssetJournal = buildUserJournal(updatedUserAsset, Direction.DEBIT, request.amount(), "DEPOSIT", request.txId(), "User deposit", eventTime);
        UserJournal userEquityJournal = buildUserJournal(updatedUserEquity, Direction.CREDIT, request.amount(), "DEPOSIT", request.txId(), "User equity increase", eventTime);
        PlatformJournal platformAssetJournal = buildPlatformJournal(updatedPlatformAsset, Direction.DEBIT, request.amount(), "DEPOSIT", request.txId(), "Platform asset increase", eventTime);
        PlatformJournal platformLiabilityJournal = buildPlatformJournal(updatedPlatformLiability, Direction.CREDIT, request.amount(), "DEPOSIT", request.txId(), "Platform liability increase", eventTime);
        
        userJournalRepository.insert(userAssetJournal);
        userJournalRepository.insert(userEquityJournal);
        platformJournalRepository.insert(platformAssetJournal);
        platformJournalRepository.insert(platformLiabilityJournal);
        
        return new AccountDepositResult(updatedUserAsset, updatedUserEquity, updatedPlatformAsset, updatedPlatformLiability,
                userAssetJournal, userEquityJournal, platformAssetJournal, platformLiabilityJournal);
    }
    
    @Transactional
    public AccountWithdrawalResult withdraw(@NotNull @Valid AccountWithdrawalRequest request) {
        OpenValidator.validateOrThrow(request);
        AssetSymbol asset = UserAccount.normalizeAsset(request.asset());
        Instant eventTime = request.creditedAt() == null ? Instant.now() : request.creditedAt();
        
        UserAccount userAssetAccount = userAccountRepository.getOrCreate(
                request.userId(),
                UserAccountCode.SPOT,
                UserAccountCode.SPOT.getDisplayName(),
                null,
                AccountCategory.ASSET,
                asset);
        UserAccount userEquityAccount = userAccountRepository.getOrCreate(
                request.userId(),
                UserAccountCode.SPOT_EQUITY,
                UserAccountCode.SPOT_EQUITY.getDisplayName(),
                null,
                AccountCategory.EQUITY,
                asset);
        
        if (!userAssetAccount.hasEnoughAvailable(request.amount())) {
            throw OpenException.of(AccountErrorCode.INSUFFICIENT_FUNDS,
                                   Map.of("userId", request.userId(), "asset", asset, "available", userAssetAccount.getAvailable(), "amount", request.amount()));
        }
        
        PlatformAccount platformAssetAccount = platformAccountRepository.getOrCreate(
                PlatformAccountCode.HOT_WALLET,
                PlatformAccountCode.HOT_WALLET.getDisplayName(),
                AccountCategory.ASSET,
                asset);
        PlatformAccount platformLiabilityAccount = platformAccountRepository.getOrCreate(
                PlatformAccountCode.USER_LIABILITY,
                PlatformAccountCode.USER_LIABILITY.getDisplayName(),
                AccountCategory.LIABILITY,
                asset);
        
        UserAccount updatedUserAsset = applyUserUpdate(userAssetAccount, Direction.CREDIT, request.amount(), true, "withdrawal");
        UserAccount updatedUserEquity = applyUserUpdate(userEquityAccount, Direction.DEBIT, request.amount(), false, "withdrawal");
        PlatformAccount updatedPlatformAsset = applyPlatformUpdate(platformAssetAccount, Direction.CREDIT, request.amount(), "withdrawal");
        PlatformAccount updatedPlatformLiability = applyPlatformUpdate(platformLiabilityAccount, Direction.DEBIT, request.amount(), "withdrawal");
        
        UserJournal userAssetJournal = buildUserJournal(updatedUserAsset, Direction.CREDIT, request.amount(), "WITHDRAWAL", request.txId(), "User withdrawal", eventTime);
        UserJournal userEquityJournal = buildUserJournal(updatedUserEquity, Direction.DEBIT, request.amount(), "WITHDRAWAL", request.txId(), "User equity decrease", eventTime);
        PlatformJournal platformAssetJournal = buildPlatformJournal(updatedPlatformAsset, Direction.CREDIT, request.amount(), "WITHDRAWAL", request.txId(), "Platform payout", eventTime);
        PlatformJournal platformLiabilityJournal = buildPlatformJournal(updatedPlatformLiability, Direction.DEBIT, request.amount(), "WITHDRAWAL", request.txId(), "Platform liability decrease", eventTime);
        
        userJournalRepository.insert(userAssetJournal);
        userJournalRepository.insert(userEquityJournal);
        platformJournalRepository.insert(platformAssetJournal);
        platformJournalRepository.insert(platformLiabilityJournal);
        
        return new AccountWithdrawalResult(updatedUserAsset, updatedUserEquity, updatedPlatformAsset, updatedPlatformLiability,
                userAssetJournal, userEquityJournal, platformAssetJournal, platformLiabilityJournal);
    }
    
    private UserAccount applyUserUpdate(UserAccount current,
                                        Direction direction,
                                        BigDecimal amount,
                                        boolean affectAvailable,
                                        String action) {
        UserAccount updated = current.apply(direction, amount, affectAvailable);
        boolean updatedRows = userAccountRepository.updateSelectiveBy(
                updated,
                new LambdaUpdateWrapper<UserAccountPO>()
                        .eq(UserAccountPO::getAccountId, current.getAccountId())
                        .eq(UserAccountPO::getVersion, current.safeVersion()));
        if (!updatedRows) {
            throw OpenException.of(AccountErrorCode.OPTIMISTIC_LOCK_FAILURE, Map.of("action", action, "accountId", current.getAccountId()));
        }
        return updated;
    }
    
    private PlatformAccount applyPlatformUpdate(PlatformAccount current,
                                                Direction direction,
                                                BigDecimal amount,
                                                String action) {
        PlatformAccount updated = current.apply(direction, amount);
        boolean updatedRows = platformAccountRepository.updateSelectiveBy(
                updated,
                new LambdaUpdateWrapper<PlatformAccountPO>()
                        .eq(PlatformAccountPO::getAccountId, current.getAccountId())
                        .eq(PlatformAccountPO::getVersion, current.safeVersion()));
        if (!updatedRows) {
            throw OpenException.of(AccountErrorCode.OPTIMISTIC_LOCK_FAILURE, Map.of("action", action, "accountId", current.getAccountId()));
        }
        return updated;
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

    @Transactional
    public void settleTrade(@NotNull @Valid TradeExecutedEvent event,
                            @NotNull @Valid OrderResponse order,
                            boolean isMaker) {
        throw OpenException.of(AccountErrorCode.INVALID_EVENT,
                               Map.of("tradeId", event.tradeId(), "orderId", order.orderId(), "message", "Trade settlement not implemented for new account schema yet"));
    }
}
