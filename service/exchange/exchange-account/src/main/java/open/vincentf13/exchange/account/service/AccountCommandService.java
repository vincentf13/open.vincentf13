package open.vincentf13.exchange.account.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.domain.service.AccountPositionDomainService;
import open.vincentf13.exchange.account.domain.service.AccountTransactionDomainService;
import open.vincentf13.exchange.account.domain.service.AccountTransactionDomainService.AccountDepositResult;
import open.vincentf13.exchange.account.domain.service.AccountTransactionDomainService.AccountWithdrawalResult;
import open.vincentf13.exchange.account.sdk.rest.api.dto.AccountDepositRequest;
import open.vincentf13.exchange.account.sdk.rest.api.dto.AccountDepositResponse;
import open.vincentf13.exchange.account.sdk.rest.api.dto.AccountWithdrawalRequest;
import open.vincentf13.exchange.account.sdk.rest.api.dto.AccountWithdrawalResponse;
import open.vincentf13.exchange.common.sdk.enums.PositionIntentType;
import open.vincentf13.exchange.matching.sdk.mq.event.TradeExecutedEvent;
import open.vincentf13.exchange.position.sdk.mq.event.PositionCloseToOpenCompensationEvent;
import open.vincentf13.exchange.position.sdk.mq.event.PositionMarginReleasedEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.annotation.Validated;

@Service
@RequiredArgsConstructor
@Validated
public class AccountCommandService {
    
    private final AccountTransactionDomainService accountTransactionDomainService;
    private final AccountPositionDomainService accountPositionDomainService;
    private final TransactionTemplate transactionTemplate;
    
    @Transactional(rollbackFor = Exception.class)
    public AccountDepositResponse deposit(@NotNull @Valid AccountDepositRequest request) {
        AccountDepositResult result = accountTransactionDomainService.deposit(request);
        var userAssetJournal = result.userAssetJournal();
        var updatedUserAsset = result.userAssetAccount();
        
        return new AccountDepositResponse(
                userAssetJournal.getJournalId(),
                result.platformAssetJournal().getJournalId(),
                updatedUserAsset.getAvailable(),
                userAssetJournal.getEventTime(),
                updatedUserAsset.getUserId(),
                updatedUserAsset.getAsset(),
                request.getAmount(),
                request.getTxId()
        );
    }
    
    @Transactional(rollbackFor = Exception.class)
    public AccountWithdrawalResponse withdraw(@NotNull @Valid AccountWithdrawalRequest request) {
        AccountWithdrawalResult result = accountTransactionDomainService.withdraw(request);
        var userAssetJournal = result.userAssetJournal();
        var userAsset = result.userAssetAccount();
        
        return new AccountWithdrawalResponse(
                userAssetJournal.getJournalId(),
                result.platformAssetJournal().getJournalId(),
                userAsset.getAvailable(),
                userAssetJournal.getEventTime(),
                userAsset.getUserId(),
                userAsset.getAsset(),
                request.getAmount(),
                request.getTxId()
        );
    }
    
    public void handleTradeExecuted(@NotNull @Valid TradeExecutedEvent event) {
        transactionTemplate.executeWithoutResult(status -> {
            if (event.makerIntent() == PositionIntentType.INCREASE) {
                accountTransactionDomainService.settleTrade(event,
                                                            event.orderId(),
                                                            event.makerUserId(),
                                                            event.orderSide(),
                                                            event.makerIntent(),
                                                            true);
            }
            if (event.takerIntent() == PositionIntentType.INCREASE) {
                accountTransactionDomainService.settleTrade(event,
                                                            event.counterpartyOrderId(),
                                                            event.takerUserId(),
                                                            event.counterpartyOrderSide(),
                                                            event.takerIntent(),
                                                            false);
            }
        });
    }

    @Transactional(rollbackFor = Exception.class)
    public void handlePositionMarginReleased(@NotNull @Valid PositionMarginReleasedEvent event) {
        accountPositionDomainService.releaseMargin(event);
    }

    @Transactional(rollbackFor = Exception.class)
    public void handleCloseToOpenCompensation(@NotNull @Valid PositionCloseToOpenCompensationEvent event) {
        accountTransactionDomainService.settleCloseToOpenCompensation(event);
    }
}
