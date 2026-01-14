package open.vincentf13.exchange.test.client;

import open.vincentf13.exchange.account.sdk.rest.api.AccountApi;
import open.vincentf13.exchange.account.sdk.rest.api.dto.AccountBalanceItem;
import open.vincentf13.exchange.account.sdk.rest.api.dto.AccountBalanceSheetResponse;
import open.vincentf13.exchange.account.sdk.rest.api.dto.AccountDepositRequest;
import open.vincentf13.exchange.account.sdk.rest.api.enums.UserAccountCode;
import open.vincentf13.exchange.common.sdk.enums.AssetSymbol;
import open.vincentf13.exchange.test.client.utils.FeignClientSupport;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class AccountClient extends BaseClient {
    private final AccountApi accountApi;

    public AccountClient(String host, String token) {
        super(host);
        this.accountApi = FeignClientSupport.buildClient(
            AccountApi.class, host + "/account/api/account", token);
    }

    public void deposit(double amount) {
        AccountDepositRequest request = new AccountDepositRequest(
            null,
            AssetSymbol.USDT,
            BigDecimal.valueOf(amount),
            "setup-dep-" + UUID.randomUUID(),
            Instant.now()
        );
        FeignClientSupport.assertSuccess(accountApi.deposit(request), "account.deposit");
    }

    public AccountBalanceSheetResponse getBalanceSheet() {
        return FeignClientSupport.assertSuccess(accountApi.getBalanceSheet(null), "account.balanceSheet");
    }

    public AccountBalanceItem getSpotAccount() {
        AccountBalanceSheetResponse balanceSheet = getBalanceSheet();
        List<AccountBalanceItem> assets = balanceSheet.assets();
        if (assets == null) {
            return null;
        }
        return assets.stream()
            .filter(asset -> UserAccountCode.SPOT.equals(asset.accountCode()) && AssetSymbol.USDT.equals(asset.asset()))
            .findFirst()
            .orElse(null);
    }
}
