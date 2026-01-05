package open.vincentf13.exchange.account.sdk.rest.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import open.vincentf13.exchange.account.sdk.rest.api.dto.*;
import open.vincentf13.exchange.account.sdk.rest.api.enums.ReferenceType;
import open.vincentf13.sdk.auth.auth.Jwt;
import open.vincentf13.sdk.auth.auth.PrivateAPI;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@Validated
public interface AccountApi {
    
    @GetMapping("/balances")
    @Jwt
    @PrivateAPI
    OpenApiResponse<AccountBalanceResponse> getBalances(@RequestParam("asset") @NotBlank String asset,
                                                        @RequestParam(value = "userId", required = false) Long userId);
    
    @PostMapping("/deposits")
    @PrivateAPI
    @Jwt
    OpenApiResponse<AccountDepositResponse> deposit(@Valid @RequestBody AccountDepositRequest request);
    
    @PostMapping("/withdrawals")
    @PrivateAPI
    @Jwt
    OpenApiResponse<AccountWithdrawalResponse> withdraw(@Valid @RequestBody AccountWithdrawalRequest request);

    @GetMapping("/balance-sheet")
    @Jwt
    OpenApiResponse<AccountBalanceSheetResponse> getBalanceSheet();

    @GetMapping("/journals")
    @Jwt
    OpenApiResponse<AccountJournalResponse> getAccountJournals(@RequestParam("accountId") @NotNull Long accountId);

    @GetMapping("/journals/by-reference")
    @Jwt
    OpenApiResponse<AccountReferenceJournalResponse> getJournalsByReference(@RequestParam("referenceType") @NotNull ReferenceType referenceType,
                                                                            @RequestParam("referenceId") @NotBlank String referenceId);
}
