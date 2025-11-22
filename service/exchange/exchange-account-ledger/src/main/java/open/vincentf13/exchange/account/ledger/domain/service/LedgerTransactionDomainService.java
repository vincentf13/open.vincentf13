package open.vincentf13.exchange.account.ledger.domain.service;

import com.github.yitter.idgen.DefaultIdGenerator;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.ledger.domain.model.transaction.LedgerDepositResult;
import open.vincentf13.exchange.account.ledger.domain.model.LedgerBalance;
import open.vincentf13.exchange.account.ledger.domain.model.LedgerEntry;
import open.vincentf13.exchange.account.ledger.domain.model.PlatformAccount;
import open.vincentf13.exchange.account.ledger.domain.model.PlatformBalance;
import open.vincentf13.exchange.account.ledger.infra.persistence.repository.LedgerBalanceRepository;
import open.vincentf13.exchange.account.ledger.infra.persistence.repository.LedgerEntryRepository;
import open.vincentf13.exchange.account.ledger.infra.persistence.repository.PlatformAccountRepository;
import open.vincentf13.exchange.account.ledger.infra.persistence.repository.PlatformBalanceRepository;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.dto.LedgerBalanceAccountType;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.dto.LedgerDepositRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class LedgerTransactionDomainService {

    private final LedgerBalanceRepository ledgerBalanceRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final PlatformAccountRepository platformAccountRepository;
    private final PlatformBalanceRepository platformBalanceRepository;
    private final DefaultIdGenerator idGenerator;

    public LedgerDepositResult deposit(LedgerDepositRequest request) {
        LedgerBalanceAccountType accountType = LedgerBalanceAccountType.SPOT_MAIN;
        String normalizedAsset = LedgerBalance.normalizeAsset(request.asset());
        LedgerBalance userBalance = getOrCreateLedgerBalance(request.userId(), accountType, normalizedAsset);
        LedgerBalance balanceUpdated = retryUpdateForDeposit(userBalance, request.amount(), request.userId(), normalizedAsset);

        PlatformAccount platformAccount = getOrCreatePlatformUserDepositAccount();
        PlatformBalance platformBalance = getOrCreatePlatformBalance(platformAccount, normalizedAsset);
        PlatformBalance platformBalanceUpdated = retryUpdateForPlatformDeposit(platformBalance, request.amount(), normalizedAsset);

        Instant eventTime = request.creditedAt() != null ? request.creditedAt() : Instant.now();
        Instant createdAt = Instant.now();
        Long userEntryId = idGenerator.newLong();
        Long platformEntryId = idGenerator.newLong();

        LedgerEntry userEntry = LedgerEntry.userDeposit(
                userEntryId,
                balanceUpdated.getAccountId(),
                balanceUpdated.getUserId(),
                balanceUpdated.getAsset(),
                request.amount(),
                platformEntryId,
                balanceUpdated.getAvailable(),
                request.txId(),
                eventTime,
                createdAt
        );
        ledgerEntryRepository.insert(userEntry);

        LedgerEntry platformEntry = LedgerEntry.platformDeposit(
                platformEntryId,
                platformBalanceUpdated.getAccountId(),
                platformBalanceUpdated.getAsset(),
                request.amount(),
                userEntryId,
                platformBalanceUpdated.getBalance(),
                request.txId(),
                eventTime,
                createdAt
        );
        ledgerEntryRepository.insert(platformEntry);

        return new LedgerDepositResult(userEntry, platformEntry, balanceUpdated, platformBalanceUpdated);
    }

    private LedgerBalance getOrCreateLedgerBalance(Long userId,
                                                   LedgerBalanceAccountType accountType,
                                                   String asset) {
        return ledgerBalanceRepository.findOne(LedgerBalance.builder()
                        .userId(userId)
                        .accountType(accountType)
                        .instrumentId(null)
                        .asset(asset)
                        .build())
                .orElseGet(() -> ledgerBalanceRepository.insert(LedgerBalance.createDefault(userId, accountType, null, asset)));
    }

    private LedgerBalance retryUpdateForDeposit(LedgerBalance balance,
                                                BigDecimal amount,
                                                Long userId,
                                                String asset) {
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
            balance = reloadLedgerBalance(balance.getUserId(), balance.getAccountType(), balance.getInstrumentId(), asset);
        }
        throw new OptimisticLockingFailureException("Failed to update ledger balance for user=" + userId);
    }

    private LedgerBalance reloadLedgerBalance(Long userId,
                                              LedgerBalanceAccountType accountType,
                                              Long instrumentId,
                                              String asset) {
        return ledgerBalanceRepository.findOne(LedgerBalance.builder()
                        .userId(userId)
                        .accountType(accountType)
                        .instrumentId(instrumentId)
                        .asset(asset)
                        .build())
                .orElseThrow(() -> new IllegalStateException(
                        "Ledger balance not found for user=" + userId + ", asset=" + asset));
    }

    private PlatformAccount getOrCreatePlatformUserDepositAccount() {
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
        } catch (DuplicateKeyException | DataIntegrityViolationException ex) {
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
        } catch (DuplicateKeyException | DataIntegrityViolationException ex) {
            return platformBalanceRepository.findOne(PlatformBalance.builder()
                            .accountId(platformAccount.getAccountId())
                            .accountCode(platformAccount.getAccountCode())
                            .asset(asset)
                            .build())
                    .orElseThrow(() -> ex);
        }
    }

    private PlatformBalance retryUpdateForPlatformDeposit(PlatformBalance platformBalance,
                                                          BigDecimal amount,
                                                          String asset) {
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
