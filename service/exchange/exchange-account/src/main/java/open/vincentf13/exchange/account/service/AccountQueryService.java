package open.vincentf13.exchange.account.service;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.domain.model.PlatformJournal;
import open.vincentf13.exchange.account.domain.model.PlatformAccount;
import open.vincentf13.exchange.account.domain.model.UserAccount;
import open.vincentf13.exchange.account.domain.model.UserJournal;
import open.vincentf13.exchange.account.infra.persistence.repository.PlatformJournalRepository;
import open.vincentf13.exchange.account.infra.persistence.repository.PlatformAccountRepository;
import open.vincentf13.exchange.account.infra.persistence.repository.UserAccountRepository;
import open.vincentf13.exchange.account.infra.persistence.repository.UserJournalRepository;
import open.vincentf13.exchange.account.sdk.rest.api.dto.AccountBalanceItem;
import open.vincentf13.exchange.account.sdk.rest.api.dto.AccountBalanceResponse;
import open.vincentf13.exchange.account.sdk.rest.api.dto.AccountBalanceSheetResponse;
import open.vincentf13.exchange.account.sdk.rest.api.dto.AccountJournalItem;
import open.vincentf13.exchange.account.sdk.rest.api.dto.AccountJournalResponse;
import open.vincentf13.exchange.account.sdk.rest.api.dto.AccountReferenceJournalResponse;
import open.vincentf13.exchange.account.sdk.rest.api.dto.PlatformAccountItem;
import open.vincentf13.exchange.account.sdk.rest.api.dto.PlatformAccountJournalResponse;
import open.vincentf13.exchange.account.sdk.rest.api.dto.PlatformAccountResponse;
import open.vincentf13.exchange.account.sdk.rest.api.dto.PlatformJournalItem;
import open.vincentf13.exchange.account.sdk.rest.api.enums.AccountCategory;
import open.vincentf13.exchange.account.sdk.rest.api.enums.ReferenceType;
import open.vincentf13.exchange.account.sdk.rest.api.enums.UserAccountCode;
import open.vincentf13.exchange.common.sdk.enums.AssetSymbol;
import open.vincentf13.sdk.core.object.mapper.OpenObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    public AccountBalanceSheetResponse getBalanceSheet(@NotNull Long userId) {
        List<UserAccount> accounts = userAccountRepository.findByUserId(userId);
        Instant snapshotAt = Instant.now();
        List<AccountBalanceItem> items = accounts.stream()
                                                 .map(item -> OpenObjectMapper.convert(item, AccountBalanceItem.class))
                                                 .filter(Objects::nonNull)
                                                 .toList();
        Comparator<AccountBalanceItem> comparator = Comparator
                .comparing((AccountBalanceItem item) -> item.accountCode() != null ? item.accountCode().name() : "")
                .thenComparing(item -> item.instrumentId() != null ? item.instrumentId() : 0L)
                .thenComparing(item -> item.asset() != null ? item.asset().name() : "");
        Map<AccountCategory, List<AccountBalanceItem>> grouped = items.stream()
                                                                      .sorted(comparator)
                                                                      .collect(Collectors.groupingBy(AccountBalanceItem::category));
        List<AccountBalanceItem> assets = grouped.getOrDefault(AccountCategory.ASSET, List.of());
        List<AccountBalanceItem> liabilities = grouped.getOrDefault(AccountCategory.LIABILITY, List.of());
        List<AccountBalanceItem> equity = grouped.getOrDefault(AccountCategory.EQUITY, List.of());
        List<AccountBalanceItem> expenses = grouped.getOrDefault(AccountCategory.EXPENSE, List.of());
        List<AccountBalanceItem> revenue = grouped.getOrDefault(AccountCategory.REVENUE, List.of());
        return new AccountBalanceSheetResponse(userId, snapshotAt, assets, liabilities, equity, expenses, revenue);
    }

    public AccountJournalResponse getAccountJournals(@NotNull Long userId,
                                                     @NotNull Long accountId) {
        List<UserJournal> journals = userJournalRepository.findByAccountId(userId, accountId);
        List<AccountJournalItem> items = buildAccountJournalItems(userId, journals);
        Instant snapshotAt = journals.isEmpty() ? Instant.now() : journals.get(0).getEventTime();
        return new AccountJournalResponse(userId, accountId, snapshotAt, items);
    }

    public AccountReferenceJournalResponse getJournalsByReference(@NotNull Long userId,
                                                                  @NotNull ReferenceType referenceType,
                                                                  @NotNull String referenceId) {
        String prefix = normalizeReferencePrefix(referenceId);
        if (prefix.isBlank()) {
            return new AccountReferenceJournalResponse(userId, referenceType, prefix, Instant.now(), List.of(), List.of());
        }
        List<UserJournal> accountJournals = userJournalRepository.findByReference(userId, referenceType, prefix);
        List<AccountJournalItem> accountItems = buildAccountJournalItems(userId, accountJournals);
        List<PlatformJournal> platformJournals = platformJournalRepository.findByReference(referenceType, prefix);
        List<PlatformJournalItem> platformItems = platformJournals.stream()
                                                                  .map(item -> OpenObjectMapper.convert(item, PlatformJournalItem.class))
                                                                  .filter(Objects::nonNull)
                                                                  .toList();
        Instant snapshotAt = Instant.now();
        return new AccountReferenceJournalResponse(userId, referenceType, prefix, snapshotAt, accountItems, platformItems);
    }

    private List<AccountJournalItem> buildAccountJournalItems(@NotNull Long userId,
                                                              @NotNull List<UserJournal> journals) {
        if (journals.isEmpty()) {
            return List.of();
        }
        Map<Long, UserAccount> accountMap = userAccountRepository.findByUserId(userId).stream()
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
                                   base.description(),
                                   base.eventTime(),
                                   base.createdAt()
                           );
                       })
                       .filter(Objects::nonNull)
                       .toList();
    }

    public PlatformAccountResponse getPlatformAccounts() {
        List<PlatformAccount> accounts = platformAccountRepository.findAll();
        List<PlatformAccountItem> items = accounts.stream()
                                                  .map(item -> OpenObjectMapper.convert(item, PlatformAccountItem.class))
                                                  .filter(Objects::nonNull)
                                                  .toList();
        Comparator<PlatformAccountItem> comparator = Comparator
                .comparing((PlatformAccountItem item) -> item.accountCode() != null ? item.accountCode().name() : "")
                .thenComparing(item -> item.asset() != null ? item.asset().name() : "");
        Map<AccountCategory, List<PlatformAccountItem>> grouped = items.stream()
                                                                       .sorted(comparator)
                                                                       .collect(Collectors.groupingBy(PlatformAccountItem::category));
        List<PlatformAccountItem> assets = grouped.getOrDefault(AccountCategory.ASSET, List.of());
        List<PlatformAccountItem> liabilities = grouped.getOrDefault(AccountCategory.LIABILITY, List.of());
        List<PlatformAccountItem> equity = grouped.getOrDefault(AccountCategory.EQUITY, List.of());
        List<PlatformAccountItem> expenses = grouped.getOrDefault(AccountCategory.EXPENSE, List.of());
        List<PlatformAccountItem> revenue = grouped.getOrDefault(AccountCategory.REVENUE, List.of());
        return new PlatformAccountResponse(Instant.now(), assets, liabilities, equity, expenses, revenue);
    }

    public PlatformAccountJournalResponse getPlatformAccountJournals(@NotNull Long accountId) {
        List<PlatformJournal> journals = platformJournalRepository.findByAccountId(accountId);
        List<PlatformJournalItem> items = journals.stream()
                                                  .map(item -> OpenObjectMapper.convert(item, PlatformJournalItem.class))
                                                  .filter(Objects::nonNull)
                                                  .toList();
        Instant snapshotAt = journals.isEmpty() ? Instant.now() : journals.get(0).getEventTime();
        return new PlatformAccountJournalResponse(accountId, snapshotAt, items);
    }

    private String normalizeReferencePrefix(String referenceId) {
        if (referenceId == null) {
            return "";
        }
        String trimmed = referenceId.trim();
        int colonIndex = trimmed.indexOf(':');
        String prefix = colonIndex >= 0 ? trimmed.substring(0, colonIndex) : trimmed;
        return prefix.replaceAll("[^0-9]", "");
    }
}
