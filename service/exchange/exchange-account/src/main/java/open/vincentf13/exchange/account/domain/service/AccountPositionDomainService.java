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

    @Transactional
    public void releaseMargin(@NotNull @Valid PositionMarginReleasedEvent event) {
        AssetSymbol asset = event.asset();
        assertNoDuplicateJournal(event.userId(), asset, ReferenceType.POSITION_MARGIN_RELEASED, event.tradeId().toString());
        UserAccount marginAccount = userAccountRepository.getOrCreate(event.userId(), UserAccountCode.MARGIN, event.instrumentId(), asset);
        if (marginAccount.getBalance().compareTo(event.marginReleased()) < 0) {
            throw OpenException.of(AccountErrorCode.INSUFFICIENT_FUNDS,
                                   Map.of("userId", event.userId(), "asset", asset, "available", marginAccount.getBalance(), "amount", event.marginReleased()));
        }
        UserAccount spotAccount = userAccountRepository.getOrCreate(event.userId(), UserAccountCode.SPOT, null, asset);
        UserAccount equityAccount = userAccountRepository.getOrCreate(event.userId(), UserAccountCode.SPOT_EQUITY, null, asset);

        UserAccount updatedMargin = applyUserUpdate(marginAccount, Direction.CREDIT, event.marginReleased());
        UserAccount updatedSpot = applyUserUpdate(spotAccount, Direction.DEBIT, event.marginReleased());
        UserAccount updatedEquity = applyUserUpdate(equityAccount,
                                                    event.realizedPnl().signum() >= 0 ? Direction.CREDIT : Direction.DEBIT,
                                                    event.realizedPnl().abs());
        if (event.realizedPnl().signum() >= 0) {
            updatedSpot = applyUserUpdate(updatedSpot, Direction.DEBIT, event.realizedPnl());
        } else {
            updatedSpot = applyUserUpdate(updatedSpot, Direction.CREDIT, event.realizedPnl().abs());
        }

        userAccountRepository.updateSelectiveBatch(
                List.of(updatedMargin, updatedSpot, updatedEquity),
                List.of(marginAccount.safeVersion(), spotAccount.safeVersion(), equityAccount.safeVersion()),
                "position-margin-release");

        UserJournal marginOut = buildUserJournal(updatedMargin, Direction.CREDIT, event.marginReleased(), ReferenceType.POSITION_MARGIN_RELEASED, event.tradeId().toString(), "Margin released to spot", event.releasedAt());
        UserJournal marginIn = buildUserJournal(updatedSpot, Direction.DEBIT, event.marginReleased(), ReferenceType.POSITION_MARGIN_RELEASED, event.tradeId().toString(), "Margin returned to spot", event.releasedAt());
        UserJournal pnlJournal = buildUserJournal(updatedSpot,
                                                  event.realizedPnl().signum() >= 0 ? Direction.DEBIT : Direction.CREDIT,
                                                  event.realizedPnl().abs(),
                                                  ReferenceType.POSITION_REALIZED_PNL,
                                                  event.tradeId().toString(),
                                                  "Realized PnL settlement",
                                                  event.releasedAt());
        UserJournal equityJournal = buildUserJournal(updatedEquity,
                                                     event.realizedPnl().signum() >= 0 ? Direction.CREDIT : Direction.DEBIT,
                                                     event.realizedPnl().abs(),
                                                     ReferenceType.POSITION_REALIZED_PNL,
                                                     event.tradeId().toString(),
                                                     "Equity adjusted by realized PnL",
                                                     event.releasedAt());
        userJournalRepository.insertBatch(List.of(marginOut, marginIn, pnlJournal, equityJournal));
    }

    private UserAccount applyUserUpdate(UserAccount current,
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
                          .description(description)
                          .eventTime(eventTime)
                          .build();
    }

    private void assertNoDuplicateJournal(Long userId,
                                          AssetSymbol asset,
                                          ReferenceType referenceType,
                                          String referenceId) {
        boolean exists = userJournalRepository.findLatestByReference(userId, asset, referenceType, referenceId).isPresent();
        if (exists) {
            throw OpenException.of(AccountErrorCode.DUPLICATE_REQUEST,
                                   Map.of("userId", userId, "asset", asset, "referenceType", referenceType, "referenceId", referenceId));
        }
    }
}
