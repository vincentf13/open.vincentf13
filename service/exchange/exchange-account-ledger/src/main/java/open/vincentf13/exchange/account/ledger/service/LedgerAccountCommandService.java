package open.vincentf13.exchange.account.ledger.service;

import com.github.yitter.idgen.DefaultIdGenerator;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.ledger.domain.model.LedgerBalance;
import open.vincentf13.exchange.account.ledger.domain.model.LedgerEntry;
import open.vincentf13.exchange.account.ledger.domain.model.PlatformAccount;
import open.vincentf13.exchange.account.ledger.domain.model.PlatformBalance;
import open.vincentf13.exchange.account.ledger.infra.persistence.repository.LedgerBalanceRepository;
import open.vincentf13.exchange.account.ledger.infra.persistence.repository.LedgerEntryRepository;
import open.vincentf13.exchange.account.ledger.infra.persistence.repository.PlatformAccountRepository;
import open.vincentf13.exchange.account.ledger.infra.persistence.repository.PlatformBalanceRepository;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.dto.*;
import open.vincentf13.sdk.core.OpenValidator;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class LedgerAccountCommandService {

    private final LedgerBalanceRepository ledgerBalanceRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final PlatformAccountRepository platformAccountRepository;
    private final PlatformBalanceRepository platformBalanceRepository;
    private final DefaultIdGenerator idGenerator;

    @Transactional
    public LedgerDepositResponse deposit(LedgerDepositRequest request) {
        OpenValidator.validateOrThrow(request);
        BigDecimal amount = request.amount();


        String normalizedAsset = LedgerBalance.normalizeAsset(request.asset());
        LedgerBalance balance = getOrCreate(
                request.userId(),
                LedgerBalanceAccountType.SPOT_MAIN,
                null,
                normalizedAsset
        );
        LedgerBalance balanceUpdated = retryUpdateForDeposit(balance, amount, request.userId(), normalizedAsset);

        PlatformAccount platformAccount = getOrCreateUserDepositAccount();
        PlatformBalance platformBalance = getOrCreatePlatformBalance(platformAccount, normalizedAsset);
        PlatformBalance platformBalanceUpdated = retryUpdateForPlatformDeposit(platformBalance, amount, normalizedAsset);


        Instant creditedAt = request.creditedAt();
        Instant eventTime = creditedAt != null ? creditedAt : Instant.now();
        Instant createdAt = Instant.now();
        Long userEntryId = idGenerator.newLong();
        Long platformEntryId = idGenerator.newLong();
        LedgerEntry userEntry = LedgerEntry.builder()
                .entryId(userEntryId)
                .ownerType(LedgerEntry.OWNER_TYPE_USER)
                .accountId(balanceUpdated.getAccountId())
                .userId(balanceUpdated.getUserId())
                .asset(balanceUpdated.getAsset())
                .amount(amount)
                .direction(LedgerEntry.DIRECTION_CREDIT)
                .counterpartyEntryId(platformEntryId)
                .balanceAfter(balanceUpdated.getAvailable())
                .referenceType(LedgerEntry.ENTRY_TYPE_DEPOSIT)
                .referenceId(request.txId())
                .entryType(LedgerEntry.ENTRY_TYPE_DEPOSIT)
                .description("用戶充值")
                .eventTime(eventTime)
                .createdAt(createdAt)
                .build();
        ledgerEntryRepository.insert(userEntry);

        LedgerEntry platformEntry = LedgerEntry.builder()
                .entryId(platformEntryId)
                .ownerType(LedgerEntry.OWNER_TYPE_PLATFORM)
                .accountId(platformBalanceUpdated.getAccountId())
                .asset(platformBalanceUpdated.getAsset())
                .amount(amount)
                .direction(LedgerEntry.DIRECTION_CREDIT)
                .counterpartyEntryId(userEntryId)
                .balanceAfter(platformBalanceUpdated.getBalance())
                .referenceType(LedgerEntry.ENTRY_TYPE_DEPOSIT)
                .referenceId(request.txId())
                .entryType(LedgerEntry.ENTRY_TYPE_DEPOSIT)
                .description("User deposit liability")
                .eventTime(eventTime)
                .createdAt(createdAt)
                .build();
        ledgerEntryRepository.insert(platformEntry);

        return new LedgerDepositResponse(
                userEntry.getEntryId(),
                userEntry.getEntryId(),
                "CONFIRMED",
                balanceUpdated.getAvailable(),
                userEntry.getEventTime(),
                balanceUpdated.getUserId(),
                balanceUpdated.getAsset(),
                amount,
                request.txId()
        );
    }

    @Transactional
    public LedgerWithdrawalResponse withdraw(LedgerWithdrawalRequest request) {
        OpenValidator.validateOrThrow(request);

        String normalizedAsset = LedgerBalance.normalizeAsset(request.asset());
        LedgerBalance balance = getOrCreate(
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
                .referenceId(null)
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
        return getOrCreate(userId, accountType, instrumentId, asset);
    }

    private LedgerBalance getOrCreate(Long userId, LedgerBalanceAccountType accountType, Long instrumentId, String asset) {
        return ledgerBalanceRepository.findOne(LedgerBalance.builder()
                        .userId(userId)
                        .accountType(accountType)
                        .instrumentId(instrumentId)
                        .asset(asset)
                        .build())
                .orElseGet(() -> ledgerBalanceRepository.insert(LedgerBalance.createDefault(userId, accountType, instrumentId, asset)));
    }



    private PlatformAccount getOrCreateUserDepositAccount() {
        PlatformAccount probe = PlatformAccount.builder()
                .accountCode(PlatformAccount.ACCOUNT_CODE_USER_DEPOSIT)
                .build();
        return platformAccountRepository.findOne(probe)
                .orElseGet(this::createUserDepositAccount);
    }

    private PlatformAccount createUserDepositAccount() {
        PlatformAccount account = PlatformAccount.createUserDepositAccount();
        try {
            return platformAccountRepository.insert(account);
        } catch (DataIntegrityViolationException ex) {
            return platformAccountRepository.findOne(PlatformAccount.builder()
                            .accountCode(PlatformAccount.ACCOUNT_CODE_USER_DEPOSIT)
                            .build())
                    .orElseThrow(() -> ex);
        }
    }

    private PlatformBalance getOrCreatePlatformBalance(PlatformAccount platformAccount, String asset) {
        return platformBalanceRepository.findOne(PlatformBalance.builder()
                        .accountId(platformAccount.getAccountId())
                        .accountCode(platformAccount.getAccountCode())
                        .asset(asset)
                        .build())
                .orElseGet(() -> insertPlatformBalance(platformAccount, asset));
    }

    private PlatformBalance insertPlatformBalance(PlatformAccount platformAccount, String asset) {
        PlatformBalance newBalance = PlatformBalance.createDefault(platformAccount.getAccountId(), platformAccount.getAccountCode(), asset);
        try {
            return platformBalanceRepository.insert(newBalance);
        } catch (DataIntegrityViolationException ex) {
            return platformBalanceRepository.findOne(PlatformBalance.builder()
                            .accountId(platformAccount.getAccountId())
                            .accountCode(platformAccount.getAccountCode())
                            .asset(asset)
                            .build())
                    .orElseThrow(() -> ex);
        }
    }

    private PlatformBalance retryUpdateForPlatformDeposit(PlatformBalance platformBalance, BigDecimal amount, String asset) {
        int retries = 0;
        while (retries < 3) {
            int currentVersion = safeVersion(platformBalance);
            BigDecimal currentBalance = platformBalance.getBalance() == null ? BigDecimal.ZERO : platformBalance.getBalance();
            platformBalance.setBalance(currentBalance.add(amount));
            platformBalance.setVersion(currentVersion + 1);
            if (platformBalanceRepository.updateWithVersion(platformBalance, currentVersion)) {
                return platformBalance;
            }
            retries++;
            platformBalance = reloadPlatformBalance(platformBalance.getAccountId(), platformBalance.getAccountCode(), asset);
        }
        throw new OptimisticLockingFailureException("Failed to update platform balance for account=" + platformBalance.getAccountId());
    }

    private PlatformBalance reloadPlatformBalance(Long accountId, String accountCode, String asset) {
        return platformBalanceRepository.findOne(PlatformBalance.builder()
                        .accountId(accountId)
                        .accountCode(accountCode)
                        .asset(asset)
                        .build())
                .orElseThrow(() -> new IllegalStateException("Platform balance not found for account=" + accountId + ", asset=" + asset));
    }

    private int safeVersion(LedgerBalance balance) {
        return balance.getVersion() == null ? 0 : balance.getVersion();
    }

    private int safeVersion(PlatformBalance balance) {
        return balance.getVersion() == null ? 0 : balance.getVersion();
    }
}
