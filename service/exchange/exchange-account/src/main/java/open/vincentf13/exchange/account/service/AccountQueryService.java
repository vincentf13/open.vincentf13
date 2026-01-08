package open.vincentf13.exchange.account.service;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.domain.model.PlatformAccount;
import open.vincentf13.exchange.account.domain.model.PlatformJournal;
import open.vincentf13.exchange.account.domain.model.UserAccount;
import open.vincentf13.exchange.account.domain.model.UserJournal;
import open.vincentf13.exchange.account.infra.persistence.repository.PlatformAccountRepository;
import open.vincentf13.exchange.account.infra.persistence.repository.PlatformJournalRepository;
import open.vincentf13.exchange.account.infra.persistence.repository.UserAccountRepository;
import open.vincentf13.exchange.account.infra.persistence.repository.UserJournalRepository;
import open.vincentf13.exchange.account.sdk.rest.api.dto.*;
import open.vincentf13.exchange.account.sdk.rest.api.enums.AccountCategory;
import open.vincentf13.exchange.account.sdk.rest.api.enums.ReferenceType;
import open.vincentf13.exchange.account.sdk.rest.api.enums.UserAccountCode;
import open.vincentf13.exchange.common.sdk.enums.AssetSymbol;
import open.vincentf13.sdk.core.object.mapper.OpenObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Validated
public class AccountQueryService {
    
    private final UserAccountRepository userAccountRepository;
    private final UserJournalRepository userJournalRepository;
    private final PlatformJournalRepository platformJournalRepository;
    private final PlatformAccountRepository platformAccountRepository;
    
    public AccountBalanceResponse getBalances(@NotNull Long userId,
                                              @NotBlank String asset) {
        AssetSymbol normalizedAsset = UserAccount.normalizeAsset(asset);
        var accountCode = UserAccountCode.SPOT;
        UserAccount balance = userAccountRepository.getOrCreate(userId, accountCode, null, normalizedAsset);
        Instant snapshotAt = balance.getUpdatedAt();
        AccountBalanceItem item = OpenObjectMapper.convert(balance, AccountBalanceItem.class);
        return new AccountBalanceResponse(userId, snapshotAt, item);
    }
    
    public AccountBalanceSheetResponse getBalanceSheet(@NotNull Long userId,
                                                      Instant snapshotAt) {
        List<UserAccount> accounts = userAccountRepository.findByUserId(userId);
        Instant now = Instant.now();
        Instant earliestUpdate = resolveEarliestUpdate(accounts, now);
        Instant latestUpdate = resolveLatestUpdate(accounts, now);
        boolean isHistorical = snapshotAt != null;
        Instant effectiveSnapshot = isHistorical ? snapshotAt : now;
        Map<Long, BigDecimal> snapshotBalances = isHistorical ? buildUserSnapshotBalances(accounts, snapshotAt) : Map.of();
        List<AccountBalanceItem> items = accounts.stream()
                                                 .map(account -> toAccountBalanceItem(account, snapshotBalances.get(account.getAccountId()), isHistorical))
                                                 .filter(Objects::nonNull)
                                                 .sorted(accountBalanceComparator())
                                                 .toList();
        Map<AccountCategory, List<AccountBalanceItem>> grouped = items.stream()
                                                                      .sorted(accountBalanceComparator())
                                                                      .collect(Collectors.groupingBy(AccountBalanceItem::category));
        List<AccountBalanceItem> assets = grouped.getOrDefault(AccountCategory.ASSET, List.of());
        List<AccountBalanceItem> liabilities = grouped.getOrDefault(AccountCategory.LIABILITY, List.of());
        List<AccountBalanceItem> equity = grouped.getOrDefault(AccountCategory.EQUITY, List.of());
        List<AccountBalanceItem> expenses = grouped.getOrDefault(AccountCategory.EXPENSE, List.of());
        List<AccountBalanceItem> revenue = grouped.getOrDefault(AccountCategory.REVENUE, List.of());
        return new AccountBalanceSheetResponse(userId, effectiveSnapshot, earliestUpdate, latestUpdate, assets, liabilities, equity, expenses, revenue);
    }

    public AccountJournalResponse getAccountJournals(@NotNull Long userId,
                                                     @NotNull Long accountId) {
        List<UserJournal> journals = userJournalRepository.findByAccountId(userId, accountId);
        List<AccountJournalItem> items = buildAccountJournalItems(journals);
        Instant snapshotAt = journals.isEmpty() ? Instant.now() : journals.get(0).getEventTime();
        return new AccountJournalResponse(userId, accountId, snapshotAt, items);
    }

    public AccountReferenceJournalResponse getJournalsByReference(@NotNull Long userId,
                                                                  @NotNull ReferenceType referenceType,
                                                                  @NotNull String referenceId) {
        String normalizedReferenceId = referenceId.trim();
        if (normalizedReferenceId.isBlank()) {
            return new AccountReferenceJournalResponse(userId, referenceType, normalizedReferenceId, Instant.now(), List.of(), List.of());
        }
        List<UserJournal> accountJournals = userJournalRepository.findByReference(referenceType, normalizedReferenceId);
        List<AccountJournalItem> accountItems = buildAccountJournalItems(accountJournals);
        List<PlatformJournal> platformJournals = platformJournalRepository.findByReference(referenceType, normalizedReferenceId);
        List<PlatformJournalItem> platformItems = buildPlatformJournalItems(platformJournals);
        Instant snapshotAt = Instant.now();
        return new AccountReferenceJournalResponse(userId, referenceType, normalizedReferenceId, snapshotAt, accountItems, platformItems);
    }

    private Comparator<AccountBalanceItem> accountBalanceComparator() {
        return Comparator
                .comparing((AccountBalanceItem item) -> item.accountCode() != null ? item.accountCode().name() : "")
                .thenComparing(item -> item.instrumentId() != null ? item.instrumentId() : 0L)
                .thenComparing(item -> item.asset() != null ? item.asset().name() : "");
    }

    private Instant resolveEarliestUpdate(List<UserAccount> accounts, Instant fallback) {
        return accounts.stream()
                       .map(UserAccount::getUpdatedAt)
                       .filter(Objects::nonNull)
                       .min(Instant::compareTo)
                       .orElse(fallback);
    }

    private Instant resolveLatestUpdate(List<UserAccount> accounts, Instant fallback) {
        return accounts.stream()
                       .map(UserAccount::getUpdatedAt)
                       .filter(Objects::nonNull)
                       .max(Instant::compareTo)
                       .orElse(fallback);
    }

    private Map<Long, BigDecimal> buildUserSnapshotBalances(List<UserAccount> accounts, Instant snapshotAt) {
        Map<Long, BigDecimal> snapshot = new HashMap<>();
        if (snapshotAt == null || accounts.isEmpty()) {
            return snapshot;
        }
        List<Long> accountIds = accounts.stream()
                                        .map(UserAccount::getAccountId)
                                        .filter(Objects::nonNull)
                                        .distinct()
                                        .toList();
        if (accountIds.isEmpty()) {
            return snapshot;
        }
        Map<Long, UserJournal> journals = userJournalRepository.findLatestBefore(accountIds, snapshotAt);
        for (Map.Entry<Long, UserJournal> entry : journals.entrySet()) {
            UserJournal journal = entry.getValue();
            if (journal == null) {
                continue;
            }
            snapshot.put(entry.getKey(), journal.getBalanceAfter());
        }
        return snapshot;
    }

    private AccountBalanceItem toAccountBalanceItem(UserAccount account,
                                                   BigDecimal overrideBalance,
                                                   boolean isHistorical) {
        if (account == null) {
            return null;
        }
        return new AccountBalanceItem(
                account.getAccountId(),
                account.getAccountCode(),
                account.getAccountName(),
                account.getCategory(),
                account.getInstrumentId(),
                account.getAsset(),
                isHistorical ? overrideBalance : account.getBalance(),
                isHistorical ? null : account.getAvailable(),
                isHistorical ? null : account.getReserved(),
                isHistorical ? null : account.getVersion(),
                isHistorical ? null : account.getCreatedAt(),
                isHistorical ? null : account.getUpdatedAt()
        );
    }

    private Comparator<PlatformAccountItem> platformAccountComparator() {
        return Comparator
                .comparing((PlatformAccountItem item) -> item.accountCode() != null ? item.accountCode().name() : "")
                .thenComparing(item -> item.asset() != null ? item.asset().name() : "")
                .thenComparing(item -> item.category() != null ? item.category().name() : "");
    }

    private Map<Long, BigDecimal> buildPlatformSnapshotBalances(List<PlatformAccount> accounts, Instant snapshotAt) {
        Map<Long, BigDecimal> snapshot = new HashMap<>();
        if (snapshotAt == null || accounts.isEmpty()) {
            return snapshot;
        }
        for (PlatformAccount account : accounts) {
            Long accountId = account.getAccountId();
            if (accountId == null) {
                continue;
            }
            platformJournalRepository.findLatestBefore(accountId, snapshotAt)
                                     .map(PlatformJournal::getBalanceAfter)
                                     .ifPresent(balance -> snapshot.put(accountId, balance));
        }
        return snapshot;
    }

    private PlatformAccountItem toPlatformAccountItem(PlatformAccount account,
                                                     BigDecimal overrideBalance) {
        if (account == null) {
            return null;
        }
        PlatformAccountItem base = OpenObjectMapper.convert(account, PlatformAccountItem.class);
        if (base == null) {
            return null;
        }
        if (overrideBalance == null) {
            return base;
        }
        return new PlatformAccountItem(
                base.accountId(),
                base.accountCode(),
                base.accountName(),
                base.category(),
                base.asset(),
                overrideBalance,
                base.version(),
                base.createdAt(),
                base.updatedAt()
        );
    }

    private List<AccountJournalItem> buildAccountJournalItems(@NotNull List<UserJournal> journals) {
        if (journals.isEmpty()) {
            return List.of();
        }
        Set<Long> userIds = journals.stream()
                                    .map(UserJournal::getUserId)
                                    .filter(Objects::nonNull)
                                    .collect(Collectors.toSet());
        List<UserAccount> accounts = userIds.isEmpty() ? List.of() : userAccountRepository.findByUserIds(new ArrayList<>(userIds));
        Map<Long, UserAccount> accountMap = accounts.stream()
                                                    .filter(Objects::nonNull)
                                                    .filter(item -> item.getAccountId() != null)
                                                    .collect(Collectors.toMap(UserAccount::getAccountId, Function.identity(), (left, right) -> left, HashMap::new));
        return journals.stream()
                       .map(item -> {
                           AccountJournalItem base = OpenObjectMapper.convert(item, AccountJournalItem.class);
                           if (base == null) {
                               return null;
                           }
                           UserAccount account = accountMap.get(item.getAccountId());
                           return new AccountJournalItem(
                                   base.journalId(),
                                   base.userId(),
                                   base.accountId(),
                                   account != null ? account.getAccountCode() : null,
                                   account != null ? account.getAccountName() : null,
                                   base.category(),
                                   base.asset(),
                                   base.amount(),
                                   base.direction(),
                                   base.balanceAfter(),
                                   base.referenceType(),
                                   base.referenceId(),
                                   base.seq(),
                                   base.description(),
                                   base.eventTime(),
                                   base.createdAt()
                           );
                       })
                       .filter(Objects::nonNull)
                       .toList();
    }

    public PlatformAccountResponse getPlatformAccounts(Instant snapshotAt) {
        List<PlatformAccount> accounts = platformAccountRepository.findAll();
        Instant now = Instant.now();
        Instant effectiveSnapshot = snapshotAt != null ? snapshotAt : now;
        Map<Long, BigDecimal> snapshotBalances = snapshotAt != null ? buildPlatformSnapshotBalances(accounts, snapshotAt) : Map.of();
        List<PlatformAccountItem> items = accounts.stream()
                                                  .map(account -> toPlatformAccountItem(account, snapshotBalances.get(account.getAccountId())))
                                                  .filter(Objects::nonNull)
                                                  .sorted(platformAccountComparator())
                                                  .toList();
        Comparator<PlatformAccountItem> comparator = platformAccountComparator();
        Map<AccountCategory, List<PlatformAccountItem>> grouped = items.stream()
                                                                       .sorted(comparator)
                                                                       .collect(Collectors.groupingBy(PlatformAccountItem::category));
        List<PlatformAccountItem> assets = grouped.getOrDefault(AccountCategory.ASSET, List.of());
        List<PlatformAccountItem> liabilities = grouped.getOrDefault(AccountCategory.LIABILITY, List.of());
        List<PlatformAccountItem> equity = grouped.getOrDefault(AccountCategory.EQUITY, List.of());
        List<PlatformAccountItem> expenses = grouped.getOrDefault(AccountCategory.EXPENSE, List.of());
        List<PlatformAccountItem> revenue = grouped.getOrDefault(AccountCategory.REVENUE, List.of());
        return new PlatformAccountResponse(effectiveSnapshot, assets, liabilities, equity, expenses, revenue);
    }

    public PlatformAccountJournalResponse getPlatformAccountJournals(@NotNull Long accountId) {
        List<PlatformJournal> journals = platformJournalRepository.findByAccountId(accountId);
        List<PlatformJournalItem> items = buildPlatformJournalItems(journals);
        Instant snapshotAt = journals.isEmpty() ? Instant.now() : journals.get(0).getEventTime();
        return new PlatformAccountJournalResponse(accountId, snapshotAt, items);
    }

    private List<PlatformJournalItem> buildPlatformJournalItems(@NotNull List<PlatformJournal> journals) {
        if (journals.isEmpty()) {
            return List.of();
        }
        List<Long> accountIds = journals.stream()
                                        .map(PlatformJournal::getAccountId)
                                        .filter(Objects::nonNull)
                                        .distinct()
                                        .toList();
        Map<Long, PlatformAccount> accountMap = platformAccountRepository.findByAccountIds(accountIds).stream()
                                                                         .filter(Objects::nonNull)
                                                                         .filter(item -> item.getAccountId() != null)
                                                                         .collect(Collectors.toMap(PlatformAccount::getAccountId, Function.identity(), (left, right) -> left, HashMap::new));
        return journals.stream()
                       .map(item -> {
                           PlatformJournalItem base = OpenObjectMapper.convert(item, PlatformJournalItem.class);
                           if (base == null) {
                               return null;
                           }
                           PlatformAccount account = accountMap.get(item.getAccountId());
                           return new PlatformJournalItem(
                                   base.journalId(),
                                   base.accountId(),
                                   account != null ? account.getAccountCode() : null,
                                   account != null ? account.getAccountName() : null,
                                   base.category(),
                                   base.asset(),
                                   base.amount(),
                                   base.direction(),
                                   base.balanceAfter(),
                                   base.referenceType(),
                                   base.referenceId(),
                                   base.seq(),
                                   base.description(),
                                   base.eventTime(),
                                   base.createdAt()
                           );
                       })
                       .filter(Objects::nonNull)
                       .toList();
    }

}
