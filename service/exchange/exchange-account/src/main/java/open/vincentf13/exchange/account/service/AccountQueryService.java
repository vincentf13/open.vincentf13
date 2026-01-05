package open.vincentf13.exchange.account.service;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.domain.model.UserAccount;
import open.vincentf13.exchange.account.infra.persistence.repository.UserAccountRepository;
import open.vincentf13.exchange.account.sdk.rest.api.dto.AccountBalanceItem;
import open.vincentf13.exchange.account.sdk.rest.api.dto.AccountBalanceResponse;
import open.vincentf13.exchange.account.sdk.rest.api.dto.AccountBalanceSheetResponse;
import open.vincentf13.exchange.account.sdk.rest.api.enums.AccountCategory;
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

@Service
@RequiredArgsConstructor
@Validated
public class AccountQueryService {
    
    private final UserAccountRepository userAccountRepository;
    
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
                                                                      .collect(java.util.stream.Collectors.groupingBy(AccountBalanceItem::category));
        List<AccountBalanceItem> assets = grouped.getOrDefault(AccountCategory.ASSET, List.of());
        List<AccountBalanceItem> liabilities = grouped.getOrDefault(AccountCategory.LIABILITY, List.of());
        List<AccountBalanceItem> equity = grouped.getOrDefault(AccountCategory.EQUITY, List.of());
        List<AccountBalanceItem> expenses = grouped.getOrDefault(AccountCategory.EXPENSE, List.of());
        List<AccountBalanceItem> revenue = grouped.getOrDefault(AccountCategory.REVENUE, List.of());
        return new AccountBalanceSheetResponse(userId, snapshotAt, assets, liabilities, equity, expenses, revenue);
    }
}
