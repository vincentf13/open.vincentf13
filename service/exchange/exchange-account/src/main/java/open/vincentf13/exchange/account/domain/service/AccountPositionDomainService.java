package open.vincentf13.exchange.account.domain.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.domain.model.UserAccount;
import open.vincentf13.exchange.account.domain.model.UserJournal;
import open.vincentf13.exchange.account.infra.AccountErrorCode;
import open.vincentf13.exchange.account.infra.persistence.repository.UserAccountRepository;
import open.vincentf13.exchange.account.infra.persistence.repository.UserJournalRepository;
import open.vincentf13.exchange.account.sdk.rest.api.enums.ReferenceType;
import open.vincentf13.exchange.account.sdk.rest.api.enums.UserAccountCode;
import open.vincentf13.exchange.common.sdk.enums.AssetSymbol;
import open.vincentf13.exchange.common.sdk.enums.Direction;
import open.vincentf13.exchange.position.sdk.mq.event.PositionMarginReleasedEvent;
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
public class AccountPositionDomainService {

    private final UserAccountRepository userAccountRepository;
    private final UserJournalRepository userJournalRepository;

    @Transactional(rollbackFor = Exception.class)
    public void releaseMargin(@NotNull @Valid PositionMarginReleasedEvent event) {
        AssetSymbol asset = event.asset();
        String referenceId = event.tradeId() + ":" + event.side().name();
        UserAccount marginAccount = userAccountRepository.getOrCreate(event.userId(), UserAccountCode.MARGIN, event.instrumentId(), asset);
        assertNoDuplicateJournal(marginAccount.getAccountId(), asset, ReferenceType.POSITION_MARGIN_RELEASED, referenceId);
        if (marginAccount.getBalance().compareTo(event.marginReleased()) < 0) {
            throw OpenException.of(AccountErrorCode.INSUFFICIENT_FUNDS,
                                   Map.of("userId", event.userId(), "asset", asset, "available", marginAccount.getBalance(), "amount", event.marginReleased()));
        }
        UserAccount spotAccount = userAccountRepository.getOrCreate(event.userId(), UserAccountCode.SPOT, null, asset);
        UserAccount realizedPnlEquity = userAccountRepository.getOrCreate(event.userId(), UserAccountCode.REALIZED_PNL_EQUITY, null, asset);
        UserAccount realizedPnlRevenue = userAccountRepository.getOrCreate(event.userId(), UserAccountCode.REALIZED_PNL_REVENUE, null, asset);
        UserAccount feeExpenseAccount = userAccountRepository.getOrCreate(event.userId(), UserAccountCode.FEE_EXPENSE, null, asset);
        UserAccount feeEquityAccount = userAccountRepository.getOrCreate(event.userId(), UserAccountCode.FEE_EQUITY, null, asset);
        
        UserAccount updatedMargin = marginAccount.apply(Direction.CREDIT, event.marginReleased());
        UserAccount spotAfterMargin = spotAccount.apply(Direction.DEBIT, event.marginReleased());
        Direction direction = event.realizedPnl().signum() >= 0 ? Direction.CREDIT : Direction.DEBIT;
        UserAccount updatedRealizedEquity = realizedPnlEquity.applyAllowNegative(direction, event.realizedPnl().abs());
        UserAccount updatedRealizedRevenue = realizedPnlRevenue.applyAllowNegative(direction, event.realizedPnl().abs());
        UserAccount updatedSpot;
        if (event.realizedPnl().signum() >= 0) {
            updatedSpot = spotAfterMargin.apply(Direction.DEBIT, event.realizedPnl());
        } else {
            updatedSpot = spotAfterMargin.apply(Direction.CREDIT, event.realizedPnl().abs());
        }
        BigDecimal feeCharged = event.feeCharged();
        boolean hasFee = feeCharged.signum() > 0;
        UserAccount updatedSpotAfterFee = hasFee ? updatedSpot.apply(Direction.CREDIT, feeCharged) : updatedSpot;
        UserAccount updatedFeeExpense = hasFee ? feeExpenseAccount.apply(Direction.DEBIT, feeCharged) : feeExpenseAccount;
        UserAccount updatedFeeEquity = hasFee ? feeEquityAccount.applyAllowNegative(Direction.DEBIT, feeCharged) : feeEquityAccount;

        List<UserAccount> accountUpdates = new java.util.ArrayList<>(5);
        List<Integer> expectedVersions = new java.util.ArrayList<>(5);
        accountUpdates.add(updatedMargin);
        expectedVersions.add(marginAccount.safeVersion());
        accountUpdates.add(updatedSpotAfterFee);
        expectedVersions.add(spotAccount.safeVersion());
        accountUpdates.add(updatedRealizedEquity);
        expectedVersions.add(realizedPnlEquity.safeVersion());
        accountUpdates.add(updatedRealizedRevenue);
        expectedVersions.add(realizedPnlRevenue.safeVersion());
        if (hasFee) {
            accountUpdates.add(updatedFeeExpense);
            expectedVersions.add(feeExpenseAccount.safeVersion());
            accountUpdates.add(updatedFeeEquity);
            expectedVersions.add(feeEquityAccount.safeVersion());
        }
        userAccountRepository.updateSelectiveBatch(accountUpdates, expectedVersions, "position-margin-release");

        int seq = 1;
        UserJournal marginOut = buildUserJournal(updatedMargin, Direction.CREDIT, event.marginReleased(), ReferenceType.POSITION_MARGIN_RELEASED, referenceId, seq++, "Margin released to spot", event.releasedAt());
        UserJournal marginIn = buildUserJournal(spotAfterMargin, Direction.DEBIT, event.marginReleased(), ReferenceType.POSITION_MARGIN_RELEASED, referenceId, seq++, "Margin returned to spot", event.releasedAt());
        UserJournal pnlJournal = buildUserJournal(updatedSpot,
                                                  event.realizedPnl().signum() >= 0 ? Direction.DEBIT : Direction.CREDIT,
                                                  event.realizedPnl().abs(),
                                                  ReferenceType.POSITION_REALIZED_PNL,
                                                  referenceId,
                                                  seq++,
                                                  "Realized PnL settlement",
                                                  event.releasedAt());
        UserJournal equityJournal = buildUserJournal(updatedRealizedEquity,
                                                     event.realizedPnl().signum() >= 0 ? Direction.CREDIT : Direction.DEBIT,
                                                     event.realizedPnl().abs(),
                                                     ReferenceType.POSITION_REALIZED_PNL,
                                                     referenceId,
                                                     seq++,
                                                     "Realized PnL equity",
                                                     event.releasedAt());
        UserJournal revenueJournal = buildUserJournal(updatedRealizedRevenue,
                                                     event.realizedPnl().signum() >= 0 ? Direction.CREDIT : Direction.DEBIT,
                                                     event.realizedPnl().abs(),
                                                     ReferenceType.POSITION_REALIZED_PNL,
                                                     referenceId,
                                                     seq++,
                                                     "Realized PnL revenue",
                                                     event.releasedAt());
        List<UserJournal> journals = new java.util.ArrayList<>(6);
        journals.add(marginOut);
        journals.add(marginIn);
        journals.add(pnlJournal);
        journals.add(equityJournal);
        journals.add(revenueJournal);
        if (hasFee) {
            UserJournal feeSpotJournal = buildUserJournal(updatedSpotAfterFee,
                                                          Direction.CREDIT,
                                                          feeCharged,
                                                          ReferenceType.TRADE_FEE,
                                                          referenceId,
                                                          seq++,
                                                          "Trading fee deducted from spot",
                                                          event.releasedAt());
            UserJournal feeExpenseJournal = buildUserJournal(updatedFeeExpense,
                                                             Direction.DEBIT,
                                                             feeCharged,
                                                             ReferenceType.TRADE_FEE,
                                                             referenceId,
                                                             seq++,
                                                             "Trading fee expense",
                                                             event.releasedAt());
            UserJournal feeEquityJournal = buildUserJournal(updatedFeeEquity,
                                                            Direction.DEBIT,
                                                            feeCharged,
                                                            ReferenceType.TRADE_FEE,
                                                            referenceId,
                                                            seq++,
                                                            "Trading fee equity",
                                                            event.releasedAt());
            journals.add(feeSpotJournal);
            journals.add(feeExpenseJournal);
            journals.add(feeEquityJournal);
        }
        userJournalRepository.insertBatch(journals);
    }
    
    private UserJournal buildUserJournal(UserAccount account,
                                         Direction direction,
                                         BigDecimal amount,
                                         ReferenceType referenceType,
                                         String referenceId,
                                         Integer seq,
                                         String description,
                                         Instant eventTime) {
        return UserJournal.builder()
                          .journalId(null)
                          .userId(account.getUserId())
                          .accountId(account.getAccountId())
                          .category(account.getCategory())
                          .asset(account.getAsset())
                          .amount(amount)
                          .direction(direction)
                          .balanceAfter(account.getBalance())
                          .referenceType(referenceType)
                          .referenceId(referenceId)
                          .seq(seq)
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
}
