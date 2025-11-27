package open.vincentf13.exchange.account.ledger.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.ledger.domain.model.LedgerBalance;
import open.vincentf13.exchange.account.ledger.domain.model.LedgerEntry;
import open.vincentf13.exchange.account.ledger.domain.model.transaction.LedgerDepositResult;
import open.vincentf13.exchange.account.ledger.domain.model.transaction.LedgerWithdrawalResult;
import open.vincentf13.exchange.account.ledger.domain.service.LedgerTransactionDomainService;
import open.vincentf13.exchange.account.ledger.infra.LedgerEvent;
import open.vincentf13.exchange.account.ledger.infra.exception.FundsFreezeException;
import open.vincentf13.exchange.account.ledger.infra.exception.FundsFreezeFailureReason;
import open.vincentf13.exchange.account.ledger.infra.messaging.publisher.LedgerEventPublisher;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.dto.LedgerDepositRequest;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.dto.LedgerDepositResponse;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.dto.LedgerWithdrawalRequest;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.dto.LedgerWithdrawalResponse;
import open.vincentf13.exchange.common.sdk.enums.AssetSymbol;
import open.vincentf13.exchange.matching.sdk.mq.event.TradeExecutedEvent;
import open.vincentf13.exchange.risk.margin.sdk.mq.event.MarginPreCheckPassedEvent;
import open.vincentf13.sdk.core.log.OpenLog;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@RequiredArgsConstructor
@Validated
public class LedgerBalanceCommandService {

    private final LedgerTransactionDomainService ledgerTransactionDomainService;
    private final LedgerEventPublisher ledgerEventPublisher;

    @Transactional
    public LedgerDepositResponse deposit(@NotNull @Valid LedgerDepositRequest request) {
        LedgerDepositResult result = ledgerTransactionDomainService.deposit(request);
        LedgerEntry userEntry = result.userEntry();
        LedgerBalance updatedBalance = result.userBalance();

        return new LedgerDepositResponse(
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
    public LedgerWithdrawalResponse withdraw(@NotNull @Valid LedgerWithdrawalRequest request) {
        LedgerWithdrawalResult result = ledgerTransactionDomainService.withdraw(request);
        LedgerEntry entry = result.entry();
        LedgerBalance balance = result.userBalance();

        return new LedgerWithdrawalResponse(
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

    @Transactional
    public void handleMarginPreCheckPassed(@NotNull @Valid MarginPreCheckPassedEvent event) {
        try {
            AssetSymbol normalizedAsset = LedgerBalance.normalizeAsset(event.asset());
            LedgerEntry entry = ledgerTransactionDomainService.freezeForOrder(
                    event.orderId(),
                    event.userId(),
                    normalizedAsset,
                    event.requiredMargin(),
                    event.checkedAt());
            ledgerEventPublisher.publishFundsFrozen(event.orderId(), event.userId(), normalizedAsset, entry.getAmount());
        } catch (FundsFreezeException ex) {
            ledgerEventPublisher.publishFundsFreezeFailed(event.orderId(), ex.getReason().name());
            OpenLog.warn(LedgerEvent.FUNDS_FREEZE_FAILED, ex, "orderId", event.orderId(), "reason", ex.getReason());
        } catch (IllegalArgumentException ex) {
            ledgerEventPublisher.publishFundsFreezeFailed(event.orderId(), FundsFreezeFailureReason.INVALID_EVENT.name());
            OpenLog.warn(LedgerEvent.INVALID_MARGIN_PRECHECK_EVENT, "orderId", event.orderId(), "message", ex.getMessage());
        }
    }

    @Transactional
    public void handleTradeExecuted(@NotNull @Valid TradeExecutedEvent event) {
        ledgerTransactionDomainService.settleTrade(
                event.tradeId(),
                event.instrumentId(), event.quoteAsset(), event.counterpartyOrderId(), event.orderId(),
                event.price(),
                event.quantity(),
                event.makerFee(),
                event.takerFee(),
                event.makerUserId(),
                event.takerUserId(),
                event.executedAt()
        );
    }
}
