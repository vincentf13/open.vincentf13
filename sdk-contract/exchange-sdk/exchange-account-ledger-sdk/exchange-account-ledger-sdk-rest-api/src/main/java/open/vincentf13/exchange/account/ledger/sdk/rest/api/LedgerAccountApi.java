package open.vincentf13.exchange.account.ledger.sdk.rest.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.dto.LedgerBalanceResponse;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.dto.LedgerDepositRequest;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.dto.LedgerDepositResponse;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.dto.LedgerWithdrawalRequest;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.dto.LedgerWithdrawalResponse;
import open.vincentf13.sdk.auth.auth.Jwt;
import open.vincentf13.sdk.auth.auth.PrivateAPI;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@Validated
public interface LedgerAccountApi {

    @GetMapping("/balances")
    @Jwt
    @PrivateAPI
    OpenApiResponse<LedgerBalanceResponse> getBalances(@RequestParam("asset") @NotBlank String asset,
                                                       @RequestParam(value = "userId", required = false) Long userId);

    @PostMapping("/deposits")
    @PrivateAPI
    OpenApiResponse<LedgerDepositResponse> deposit(@Valid @RequestBody LedgerDepositRequest request);

    @PostMapping("/withdrawals")
    @PrivateAPI
    OpenApiResponse<LedgerWithdrawalResponse> withdraw(@Valid @RequestBody LedgerWithdrawalRequest request);
}
