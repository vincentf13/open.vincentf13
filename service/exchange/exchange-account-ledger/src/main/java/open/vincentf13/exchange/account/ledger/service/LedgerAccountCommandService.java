package open.vincentf13.exchange.account.ledger.service;

import com.github.yitter.idgen.DefaultIdGenerator;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.ledger.domain.model.LedgerBalance;
import open.vincentf13.exchange.account.ledger.domain.model.LedgerBalanceAccountType;
import open.vincentf13.exchange.account.ledger.domain.model.LedgerEntry;
import open.vincentf13.exchange.account.ledger.domain.model.LedgerWithdrawalRecord;
import open.vincentf13.exchange.account.ledger.infra.persistence.repository.LedgerBalanceRepository;
import open.vincentf13.exchange.account.ledger.infra.persistence.repository.LedgerEntryRepository;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.dto.LedgerDepositRequest;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.dto.LedgerDepositResponse;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.dto.LedgerWithdrawalRequest;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.dto.LedgerWithdrawalResponse;
import open.vincentf13.sdk.core.OpenMapstruct;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class LedgerAccountCommandService {

    private static final String OWNER_TYPE_USER = "USER";
    private static final String ENTRY_DIRECTION_CREDIT = "CREDIT";
    private static final String ENTRY_DIRECTION_DEBIT = "DEBIT";
    private static final String ENTRY_TYPE_DEPOSIT = "DEPOSIT";
    private static final String ENTRY_TYPE_WITHDRAWAL = "WITHDRAWAL";

    private final LedgerBalanceRepository ledgerBalanceRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final DefaultIdGenerator idGenerator;

    @Transactional
    public LedgerDepositResponse deposit(LedgerDepositRequest request) {
        validateDepositRequest(request);
        LedgerBalance balance = initializeAmounts(
                getOrDefault(
                        request.userId(),
                        defaultAccountType(LedgerBalanceAccountType.fromValue(request.accountType())),
                        request.instrumentId(),
                        normalizeAsset(request.asset())
                )
        );

        int currentVersion = safeVersion(balance);
        BigDecimal amount = request.amount();
        balance.setBalance(balance.getBalance().add(amount));
        balance.setAvailable(balance.getAvailable().add(amount));
        balance.setTotalDeposited(balance.getTotalDeposited().add(amount));
        balance.setVersion(currentVersion + 1);
        if (!ledgerBalanceRepository.updateWithVersion(balance, currentVersion)) {
            throw new OptimisticLockingFailureException("Failed to update ledger balance for user=" + request.userId());
        }

        LedgerEntry entry = buildLedgerEntry(balance, amount, request.creditedAt(),
                request.channel(), request.metadata(), ENTRY_TYPE_DEPOSIT, ENTRY_DIRECTION_CREDIT);
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
                request.txId(),
                request.channel()
        );
    }

    @Transactional
    public LedgerWithdrawalResponse withdraw(LedgerWithdrawalRequest request) {
        validateWithdrawalRequest(request);
        LedgerBalance balance = initializeAmounts(
                getOrDefault(
                        request.userId(),
                        defaultAccountType(LedgerBalanceAccountType.fromValue(request.accountType())),
                        request.instrumentId(),
                        normalizeAsset(request.asset())
                )
        );

        BigDecimal fee = request.fee() == null ? BigDecimal.ZERO : request.fee();
        BigDecimal totalOut = request.amount().add(fee);
        if (balance.getAvailable().compareTo(totalOut) < 0) {
            throw new IllegalArgumentException("Insufficient available balance");
        }

        int currentVersion = safeVersion(balance);
        balance.setAvailable(balance.getAvailable().subtract(totalOut));
        balance.setBalance(balance.getBalance().subtract(totalOut));
        balance.setTotalWithdrawn(balance.getTotalWithdrawn().add(request.amount()));
        balance.setVersion(currentVersion + 1);
        if (!ledgerBalanceRepository.updateWithVersion(balance, currentVersion)) {
            throw new OptimisticLockingFailureException("Failed to update ledger balance for withdrawal");
        }

        LedgerEntry entry = buildLedgerEntry(balance, totalOut, request.requestedAt(),
                request.destination(), request.metadata(), ENTRY_TYPE_WITHDRAWAL, ENTRY_DIRECTION_DEBIT);
        ledgerEntryRepository.insert(entry);

        LedgerWithdrawalRecord record = LedgerWithdrawalRecord.builder()
                .withdrawalId(entry.getEntryId())
                .reservedEntryId(entry.getEntryId())
                .status("REQUESTED")
                .balanceAfter(balance.getAvailable())
                .requestedAt(entry.getEventTime())
                .userId(balance.getUserId())
                .asset(balance.getAsset())
                .amount(request.amount())
                .fee(fee)
                .externalRef(request.externalRef())
                .build();
        return OpenMapstruct.map(record, LedgerWithdrawalResponse.class);
    }

    private LedgerEntry buildLedgerEntry(LedgerBalance balance,
                                         BigDecimal amount,
                                         Instant eventTime,
                                         String description,
                                         String metadata,
                                         String entryType,
                                         String direction) {
        Long entryId = idGenerator.newLong();
        return LedgerEntry.builder()
                .entryId(entryId)
                .ownerType(OWNER_TYPE_USER)
                .accountId(balance.getAccountId())
                .userId(balance.getUserId())
                .asset(balance.getAsset())
                .amount(amount)
                .direction(direction)
                .balanceAfter(balance.getAvailable())
                .referenceType(entryType)
                .referenceId(entryId)
                .entryType(entryType)
                .description(description)
                .metadata(metadata)
                .eventTime(eventTime != null ? eventTime : Instant.now())
                .createdAt(Instant.now())
                .build();
    }

    private void validateDepositRequest(LedgerDepositRequest request) {
        if (request.userId() == null) {
            throw new IllegalArgumentException("userId is required");
        }
        if (!StringUtils.hasText(request.asset())) {
            throw new IllegalArgumentException("asset is required");
        }
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }

    private void validateWithdrawalRequest(LedgerWithdrawalRequest request) {
        if (request.userId() == null) {
            throw new IllegalArgumentException("userId is required");
        }
        if (!StringUtils.hasText(request.asset())) {
            throw new IllegalArgumentException("asset is required");
        }
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (request.fee() != null && request.fee().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("fee cannot be negative");
        }
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

    private LedgerBalance initializeAmounts(LedgerBalance balance) {
        if (balance.getBalance() == null) {
            balance.setBalance(BigDecimal.ZERO);
        }
        if (balance.getAvailable() == null) {
            balance.setAvailable(BigDecimal.ZERO);
        }
        if (balance.getReserved() == null) {
            balance.setReserved(BigDecimal.ZERO);
        }
        if (balance.getTotalDeposited() == null) {
            balance.setTotalDeposited(BigDecimal.ZERO);
        }
        if (balance.getTotalWithdrawn() == null) {
            balance.setTotalWithdrawn(BigDecimal.ZERO);
        }
        if (balance.getTotalPnl() == null) {
            balance.setTotalPnl(BigDecimal.ZERO);
        }
        return balance;
    }

    private LedgerBalanceAccountType defaultAccountType(LedgerBalanceAccountType accountType) {
        return accountType != null ? accountType : LedgerBalanceAccountType.SPOT_MAIN;
    }

    private String normalizeAsset(String asset) {
        return asset == null ? null : asset.toUpperCase(Locale.ROOT);
    }

    private int safeVersion(LedgerBalance balance) {
        return balance.getVersion() == null ? 0 : balance.getVersion();
    }
}
