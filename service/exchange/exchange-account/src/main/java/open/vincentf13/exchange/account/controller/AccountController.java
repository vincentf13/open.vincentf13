package open.vincentf13.exchange.account.controller;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.sdk.rest.api.AccountApi;
import open.vincentf13.exchange.account.sdk.rest.api.dto.*;
import open.vincentf13.exchange.account.service.AccountCommandService;
import open.vincentf13.exchange.account.service.AccountQueryService;
import open.vincentf13.sdk.auth.jwt.OpenJwtLoginUserHolder;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class AccountController implements AccountApi {
    
    private final AccountQueryService accountQueryService;
    private final AccountCommandService accountCommandService;
    
    @Override
    public OpenApiResponse<AccountBalanceResponse> getBalances(String asset,
                                                               Long userId) {
        Long jwtUserId = OpenJwtLoginUserHolder.currentUserId();
        Long effectiveUserId = jwtUserId != null
                               ? jwtUserId
                               : userId;
        if (effectiveUserId == null) {
            throw new IllegalArgumentException("Missing user context");
        }
        return OpenApiResponse.success(accountQueryService.getBalances(effectiveUserId, asset));
    }
    
    @Override
    public OpenApiResponse<AccountDepositResponse> deposit(AccountDepositRequest request) {
        extracted(request);
        return OpenApiResponse.success(accountCommandService.deposit(request));
    }
    
    @Override
    public OpenApiResponse<AccountWithdrawalResponse> withdraw(AccountWithdrawalRequest request) {
        extracted(request);
        return OpenApiResponse.success(accountCommandService.withdraw(request));
    }
    
    private static void extracted(AccountWithdrawalRequest request) {
        Long jwtUserId = OpenJwtLoginUserHolder.currentUserId();
        if (jwtUserId != null) {
            request.setUserId(jwtUserId);
        }
        if (request.getUserId() == null) {
            throw new IllegalArgumentException("Missing user context");
        }
    }
}
