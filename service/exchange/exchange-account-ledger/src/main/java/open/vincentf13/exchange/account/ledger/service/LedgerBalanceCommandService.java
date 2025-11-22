package open.vincentf13.exchange.account.ledger.service;

import com.github.yitter.idgen.DefaultIdGenerator;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.ledger.domain.model.transaction.LedgerDepositResult;
import open.vincentf13.exchange.account.ledger.domain.model.LedgerBalance;
import open.vincentf13.exchange.account.ledger.domain.model.LedgerEntry;
import open.vincentf13.exchange.account.ledger.domain.service.LedgerTransactionDomainService;
import open.vincentf13.exchange.account.ledger.infra.persistence.repository.LedgerBalanceRepository;
import open.vincentf13.exchange.account.ledger.infra.persistence.repository.LedgerEntryRepository;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.dto.*;
import open.vincentf13.sdk.core.OpenValidator;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class LedgerBalanceCommandService {

    private final LedgerBalanceRepository ledgerBalanceRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final LedgerTransactionDomainService ledgerTransactionDomainService;
    private final DefaultIdGenerator idGenerator;

    @Transactional
    public LedgerDepositResponse deposit(LedgerDepositRequest request) {
        OpenValidator.validateOrThrow(request);
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
    public LedgerWithdrawalResponse withdraw(LedgerWithdrawalRequest request) {
        OpenValidator.validateOrThrow(request);

        String normalizedAsset = LedgerBalance.normalizeAsset(request.asset());
        LedgerBalance balance = getOrCreate(
                request.userId(),
                AccountType.SPOT_MAIN,
                request.instrumentId(),
                normalizedAsset
        );

        BigDecimal fee = request.fee() == null ? BigDecimal.ZERO : request.fee();
        BigDecimal totalOut = request.amount().add(fee);
        if (balance.getAvailable().compareTo(totalOut) < 0) {
            throw new IllegalArgumentException("Insufficient available balance");
        }

        balance = retryUpdateForWithdrawal(balance, totalOut, request.amount(), request.userId(), normalizedAsset);

        Instant eventTime = request.requestedAt() != null ? request.requestedAt() : Instant.now();
        Instant createdAt = Instant.now();
        Long entryId = idGenerator.newLong();
        LedgerEntry entry = LedgerEntry.userWithdrawal(
                entryId,
                balance.getAccountId(),
                balance.getUserId(),
                balance.getAsset(),
                totalOut,
                balance.getAvailable(),
                null,
                request.destination(),
                request.metadata(),
                eventTime,
                createdAt
        );
        ledgerEntryRepository.insert(entry);

        return new LedgerWithdrawalResponse(
                entry.getEntryId(),
                entry.getEntryId(),
                "REQUESTED",
                balance.getAvailable(),
                entry.getEventTime(),
                balance.getUserId(),
                balance.getAsset(),
                request.amount(),
                fee,
                request.externalRef()
        );
    }

    private LedgerBalance retryUpdateForWithdrawal(LedgerBalance balance, BigDecimal totalOut, BigDecimal amount, Long userId, String asset) {
        int retries = 0;
        while (retries < 3) {
            int currentVersion = safeVersion(balance);
            balance.setAvailable(balance.getAvailable().subtract(totalOut));
            balance.setBalance(balance.getBalance().subtract(totalOut));
            balance.setTotalWithdrawn(balance.getTotalWithdrawn().add(amount));
            balance.setVersion(currentVersion + 1);
            if (ledgerBalanceRepository.updateWithVersion(balance, currentVersion)) {
                return balance;
            }
            retries++;
            balance = reloadBalance(balance.getUserId(), balance.getAccountType(), balance.getInstrumentId(), asset);
        }
        throw new OptimisticLockingFailureException("Failed to update ledger balance for user=" + userId);
    }

    private LedgerBalance reloadBalance(Long userId, AccountType accountType, Long instrumentId, String asset) {
        return getOrCreate(userId, accountType, instrumentId, asset);
    }

    private LedgerBalance getOrCreate(Long userId, AccountType accountType, Long instrumentId, String asset) {
        return ledgerBalanceRepository.findOne(LedgerBalance.builder()
                        .userId(userId)
                        .accountType(accountType)
                        .instrumentId(instrumentId)
                        .asset(asset)
                        .build())
                .orElseGet(() -> ledgerBalanceRepository.insert(LedgerBalance.createDefault(userId, accountType, instrumentId, asset)));
    }

    private int safeVersion(LedgerBalance balance) {
        return balance.getVersion() == null ? 0 : balance.getVersion();
    }
}
