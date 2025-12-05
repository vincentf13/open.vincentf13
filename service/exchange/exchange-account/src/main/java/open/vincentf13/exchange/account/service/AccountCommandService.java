package open.vincentf13.exchange.account.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.domain.model.AccountBalance;
import open.vincentf13.exchange.account.domain.model.AccountEntry;
import open.vincentf13.exchange.account.domain.model.transaction.AccountDepositResult;
import open.vincentf13.exchange.account.domain.model.transaction.AccountWithdrawalResult;
import open.vincentf13.exchange.account.domain.service.AccountTransactionDomainService;
import open.vincentf13.exchange.account.sdk.rest.api.dto.AccountDepositRequest;
import open.vincentf13.exchange.account.sdk.rest.api.dto.AccountDepositResponse;
import open.vincentf13.exchange.account.sdk.rest.api.dto.AccountWithdrawalRequest;
import open.vincentf13.exchange.account.sdk.rest.api.dto.AccountWithdrawalResponse;
import open.vincentf13.exchange.matching.sdk.mq.event.TradeExecutedEvent;
import open.vincentf13.exchange.order.sdk.rest.client.ExchangeOrderClient;
import open.vincentf13.exchange.order.sdk.rest.dto.OrderResponse;
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
    private final TransactionTemplate transactionTemplate;
    private final ExchangeOrderClient exchangeOrderClient;
    
    @Transactional
    public AccountDepositResponse deposit(@NotNull @Valid AccountDepositRequest request) {
        AccountDepositResult result = accountTransactionDomainService.deposit(request);
        AccountEntry userEntry = result.userEntry();
        AccountBalance updatedBalance = result.userBalance();
        
        return new AccountDepositResponse(
                userEntry.getEntryId(),
                userEntry.getEntryId(),
                updatedBalance.getAvailable(),
                userEntry.getCreatedAt(),
                updatedBalance.getUserId(),
                updatedBalance.getAsset(),
                request.amount(),
                request.txId()
        );
    }
    
    @Transactional
    public AccountWithdrawalResponse withdraw(@NotNull @Valid AccountWithdrawalRequest request) {
        AccountWithdrawalResult result = accountTransactionDomainService.withdraw(request);
        AccountEntry entry = result.entry();
        AccountBalance balance = result.userBalance();
        
        return new AccountWithdrawalResponse(
                entry.getEntryId(),
                entry.getEntryId(),
                balance.getAvailable(),
                entry.getCreatedAt(),
                balance.getUserId(),
                balance.getAsset(),
                request.amount(),
                request.txId()
        );
    }
    
    public void handleTradeExecuted(@NotNull @Valid TradeExecutedEvent event) {
        OrderResponse makerOrder = OpenApiClientInvoker.call(() -> exchangeOrderClient.getOrder(event.orderId()));
        OrderResponse takerOrder = OpenApiClientInvoker.call(() -> exchangeOrderClient.getOrder(event.counterpartyOrderId()));
        
        transactionTemplate.executeWithoutResult(status -> {
            accountTransactionDomainService.settleTrade(event, makerOrder, true);
            accountTransactionDomainService.settleTrade(event, takerOrder, false);
        });
    }
}
