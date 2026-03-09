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
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class AccountClient extends BaseClient {
    public static void deposit(String token, double amount) {
        AccountApi accountApi = FeignClientSupport.buildClient(
            AccountApi.class, host() + "/account/api/account", token);
        AccountDepositRequest request = new AccountDepositRequest(
            null,
            AssetSymbol.USDT,
            BigDecimal.valueOf(amount),
            "setup-dep-" + UUID.randomUUID(),
            Instant.now()
        );
        FeignClientSupport.assertSuccess(accountApi.deposit(request), "account.deposit");
    }

    public static AccountBalanceSheetResponse getBalanceSheet(String token) {
        AccountApi accountApi = FeignClientSupport.buildClient(
            AccountApi.class, host() + "/account/api/account", token);
        return FeignClientSupport.assertSuccess(accountApi.getBalanceSheet(null), "account.balanceSheet");
    }

    public static AccountBalanceItem getSpotAccount(String token) {
        AccountBalanceSheetResponse balanceSheet = getBalanceSheet(token);
        List<AccountBalanceItem> assets = balanceSheet.assets();
        if (assets == null) {
            return null;
        }
        AccountBalanceItem spot = assets.stream()
            .filter(asset -> UserAccountCode.SPOT.equals(asset.accountCode()) && AssetSymbol.USDT.equals(asset.asset()))
            .findFirst()
            .orElse(null);
        assertNotNull(spot, "Spot account not found for baseline");
        return spot;
    }
}
