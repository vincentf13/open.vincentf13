package open.vincentf13.exchange.account.service;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.domain.model.PlatformJournal;
import open.vincentf13.exchange.account.domain.model.UserAccount;
import open.vincentf13.exchange.account.domain.model.UserJournal;
import open.vincentf13.exchange.account.infra.persistence.repository.PlatformJournalRepository;
import open.vincentf13.exchange.account.infra.persistence.repository.UserAccountRepository;
import open.vincentf13.exchange.account.infra.persistence.repository.UserJournalRepository;
import open.vincentf13.exchange.account.sdk.rest.api.dto.AccountBalanceItem;
import open.vincentf13.exchange.account.sdk.rest.api.dto.AccountBalanceResponse;
import open.vincentf13.exchange.account.sdk.rest.api.dto.AccountBalanceSheetResponse;
import open.vincentf13.exchange.account.sdk.rest.api.dto.AccountJournalItem;
import open.vincentf13.exchange.account.sdk.rest.api.dto.AccountJournalResponse;
import open.vincentf13.exchange.account.sdk.rest.api.dto.AccountReferenceJournalResponse;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Validated
public class AccountQueryService {
    
    private final UserAccountRepository userAccountRepository;
    private final UserJournalRepository userJournalRepository;
    private final PlatformJournalRepository platformJournalRepository;
    
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
        List<AccountJournalItem> items = journals.stream()
                                                 .map(item -> OpenObjectMapper.convert(item, AccountJournalItem.class))
                                                 .filter(Objects::nonNull)
                                                 .toList();
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
        List<AccountJournalItem> accountItems = accountJournals.stream()
                                                               .map(item -> OpenObjectMapper.convert(item, AccountJournalItem.class))
                                                               .filter(Objects::nonNull)
                                                               .toList();
        List<PlatformJournal> platformJournals = platformJournalRepository.findByReference(referenceType, prefix);
        List<PlatformJournalItem> platformItems = platformJournals.stream()
                                                                  .map(item -> OpenObjectMapper.convert(item, PlatformJournalItem.class))
                                                                  .filter(Objects::nonNull)
                                                                  .toList();
        Instant snapshotAt = Instant.now();
        return new AccountReferenceJournalResponse(userId, referenceType, prefix, snapshotAt, accountItems, platformItems);
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
