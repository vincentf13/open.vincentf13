package open.vincentf13.exchange.account.ledger.service;

import com.github.yitter.idgen.DefaultIdGenerator;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.ledger.domain.model.LedgerBalance;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.dto.LedgerBalanceAccountType;
import open.vincentf13.exchange.account.ledger.domain.model.LedgerEntry;
import open.vincentf13.exchange.account.ledger.infra.persistence.repository.LedgerBalanceRepository;
import open.vincentf13.exchange.account.ledger.infra.persistence.repository.LedgerEntryRepository;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.dto.LedgerDepositRequest;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.dto.LedgerDepositResponse;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.dto.LedgerWithdrawalRequest;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.dto.LedgerWithdrawalResponse;
import open.vincentf13.sdk.core.OpenValidator;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class LedgerAccountCommandService {

    private final LedgerBalanceRepository ledgerBalanceRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final DefaultIdGenerator idGenerator;

    @Transactional
    public LedgerDepositResponse deposit(LedgerDepositRequest request) {
        OpenValidator.validateOrThrow(request);

        String normalizedAsset = request.asset().toUpperCase(Locale.ROOT);
        LedgerBalance balance = getOrDefault(
                request.userId(),
                LedgerBalanceAccountType.SPOT_MAIN,
                null,
                normalizedAsset
        );

        BigDecimal amount = request.amount();
        balance = retryUpdateForDeposit(balance, amount, request.userId(), normalizedAsset);

        Instant eventTime = request.creditedAt();
        Long entryId = idGenerator.newLong();
        LedgerEntry entry = LedgerEntry.builder()
                .entryId(entryId)
                .ownerType(LedgerEntry.OWNER_TYPE_USER)
                .accountId(balance.getAccountId())
                .userId(balance.getUserId())
                .asset(balance.getAsset())
                .amount(amount)
                .direction(LedgerEntry.DIRECTION_CREDIT)
                .balanceAfter(balance.getAvailable())
                .referenceType(LedgerEntry.ENTRY_TYPE_DEPOSIT)
                .referenceId(entryId)
                .entryType(LedgerEntry.ENTRY_TYPE_DEPOSIT)
                .description(request.txId())
                .eventTime(eventTime != null ? eventTime : Instant.now())
                .createdAt(Instant.now())
                .build();
        ledgerEntryRepository.insert(entry);

        return new LedgerDepositResponse(
                entry.getEntryId(),
                entry.getEntryId(),
                "CONFIRMED",
                balance.getAvailable(),
                entry.getEventTime(),
                balance.getUserId(),
                balance.getAsset(),
                amount,
                request.txId()
        );
    }

    @Transactional
    public LedgerWithdrawalResponse withdraw(LedgerWithdrawalRequest request) {
        OpenValidator.validateOrThrow(request);

        String normalizedAsset = request.asset().toUpperCase(Locale.ROOT);
        LedgerBalance balance = getOrDefault(
                request.userId(),
                LedgerBalanceAccountType.SPOT_MAIN,
                request.instrumentId(),
                normalizedAsset
        );

        BigDecimal fee = request.fee() == null ? BigDecimal.ZERO : request.fee();
        BigDecimal totalOut = request.amount().add(fee);
        if (balance.getAvailable().compareTo(totalOut) < 0) {
            throw new IllegalArgumentException("Insufficient available balance");
        }

        balance = retryUpdateForWithdrawal(balance, totalOut, request.amount(), request.userId(), normalizedAsset);

        Instant eventTime = request.requestedAt();
        Long entryId = idGenerator.newLong();
        LedgerEntry entry = LedgerEntry.builder()
                .entryId(entryId)
                .ownerType(LedgerEntry.OWNER_TYPE_USER)
                .accountId(balance.getAccountId())
                .userId(balance.getUserId())
                .asset(balance.getAsset())
                .amount(totalOut)
                .direction(LedgerEntry.DIRECTION_DEBIT)
                .balanceAfter(balance.getAvailable())
                .referenceType(LedgerEntry.ENTRY_TYPE_WITHDRAWAL)
                .referenceId(entryId)
                .entryType(LedgerEntry.ENTRY_TYPE_WITHDRAWAL)
                .description(request.destination())
                .metadata(request.metadata())
                .eventTime(eventTime != null ? eventTime : Instant.now())
                .createdAt(Instant.now())
                .build();
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

    private LedgerBalance retryUpdateForDeposit(LedgerBalance balance, BigDecimal amount, Long userId, String asset) {
        int retries = 0;
        while (retries < 3) {
            int currentVersion = safeVersion(balance);
            balance.setBalance(balance.getBalance().add(amount));
            balance.setAvailable(balance.getAvailable().add(amount));
            balance.setTotalDeposited(balance.getTotalDeposited().add(amount));
            balance.setVersion(currentVersion + 1);
            if (ledgerBalanceRepository.updateWithVersion(balance, currentVersion)) {
                return balance;
            }
            retries++;
            balance = reloadBalance(balance.getUserId(), balance.getAccountType(), balance.getInstrumentId(), asset);
        }
        throw new OptimisticLockingFailureException("Failed to update ledger balance for user=" + userId);
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

    private LedgerBalance reloadBalance(Long userId, LedgerBalanceAccountType accountType, Long instrumentId, String asset) {
        return getOrDefault(userId, accountType, instrumentId, asset);
    }

    private LedgerBalance getOrDefault(Long userId, LedgerBalanceAccountType accountType, Long instrumentId, String asset) {
        return ledgerBalanceRepository.findOne(LedgerBalance.builder()
                        .userId(userId)
                        .accountType(accountType)
                        .instrumentId(instrumentId)
                        .asset(asset)
                        .build())
                .orElseGet(() -> ledgerBalanceRepository.insert(newBalance(userId, accountType, instrumentId, asset)));
    }

    private LedgerBalance newBalance(Long userId, LedgerBalanceAccountType accountType, Long instrumentId, String asset) {
        return LedgerBalance.builder()
                .userId(userId)
                .accountType(accountType)
                .instrumentId(instrumentId)
                .asset(asset)
                .balance(BigDecimal.ZERO)
                .available(BigDecimal.ZERO)
                .reserved(BigDecimal.ZERO)
                .totalDeposited(BigDecimal.ZERO)
                .totalWithdrawn(BigDecimal.ZERO)
                .totalPnl(BigDecimal.ZERO)
                .version(0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }


    private int safeVersion(LedgerBalance balance) {
        return balance.getVersion() == null ? 0 : balance.getVersion();
    }
}
