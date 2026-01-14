package open.vincentf13.exchange.test.client;

import open.vincentf13.exchange.account.sdk.rest.api.AccountApi;
import open.vincentf13.exchange.account.sdk.rest.api.dto.AccountBalanceSheetResponse;
import open.vincentf13.exchange.account.sdk.rest.api.dto.AccountDepositRequest;
import open.vincentf13.exchange.common.sdk.enums.AssetSymbol;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

class AccountClient {
    private final String gatewayHost;

    AccountClient(String gatewayHost) {
        this.gatewayHost = gatewayHost;
    }

    void deposit(String token, double amount) {
        AccountApi accountApi = FeignClientSupport.buildClient(
            AccountApi.class, gatewayHost + "/account/api/account", token);
        AccountDepositRequest request = new AccountDepositRequest(
            null,
            AssetSymbol.USDT,
            BigDecimal.valueOf(amount),
            "setup-dep-" + UUID.randomUUID(),
            Instant.now()
        );
        FeignClientSupport.assertSuccess(accountApi.deposit(request), "account.deposit");
    }

    AccountBalanceSheetResponse getBalanceSheet(String token) {
        AccountApi accountApi = FeignClientSupport.buildClient(
            AccountApi.class, gatewayHost + "/account/api/account", token);
        return FeignClientSupport.assertSuccess(accountApi.getBalanceSheet(null), "account.balanceSheet");
    }
}
