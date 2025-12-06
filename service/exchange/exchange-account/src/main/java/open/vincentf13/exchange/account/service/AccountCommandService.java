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
import open.vincentf13.exchange.matching.sdk.mq.event.TradeExecutedEvent;
import open.vincentf13.exchange.order.sdk.rest.client.ExchangeOrderClient;
import open.vincentf13.exchange.order.sdk.rest.dto.OrderResponse;
import open.vincentf13.exchange.position.sdk.mq.event.PositionMarginReleasedEvent;
import open.vincentf13.sdk.spring.cloud.openfeign.OpenApiClientInvoker;
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
    private final ExchangeOrderClient exchangeOrderClient;
    
    @Transactional
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
                request.amount(),
                request.txId()
        );
    }
    
    @Transactional
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
                request.amount(),
                request.txId()
        );
    }
    
    public void handleTradeExecuted(@NotNull @Valid TradeExecutedEvent event) {
        OrderResponse makerOrder = OpenApiClientInvoker.call(() -> exchangeOrderClient.getOrder(event.orderId()));
        OrderResponse takerOrder = OpenApiClientInvoker.call(() -> exchangeOrderClient.getOrder(event.counterpartyOrderId()));
        
        transactionTemplate.executeWithoutResult(status -> {
            accountTransactionDomainService.settleTrade(event, makerOrder, makerOrder.userId().equals(event.makerUserId()));
            accountTransactionDomainService.settleTrade(event, takerOrder, takerOrder.userId().equals(event.makerUserId()));
        });
    }

    @Transactional
    public void handlePositionMarginReleased(@NotNull @Valid PositionMarginReleasedEvent event) {
        accountPositionDomainService.releaseMargin(event);
    }
}
