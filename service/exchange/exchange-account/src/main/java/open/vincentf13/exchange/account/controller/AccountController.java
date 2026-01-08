package open.vincentf13.exchange.account.controller;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.sdk.rest.api.AccountApi;
import open.vincentf13.exchange.account.sdk.rest.api.dto.*;
import open.vincentf13.exchange.account.sdk.rest.api.enums.ReferenceType;
import open.vincentf13.exchange.account.service.AccountCommandService;
import open.vincentf13.exchange.account.service.AccountQueryService;
import open.vincentf13.sdk.auth.jwt.OpenJwtLoginUserHolder;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

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
        Long jwtUserId = OpenJwtLoginUserHolder.currentUserId();
        if (jwtUserId != null) {
            request.setUserId(jwtUserId);
        }
        if (request.getUserId() == null) {
            throw new IllegalArgumentException("Missing user context");
        }
        return OpenApiResponse.success(accountCommandService.deposit(request));
    }
    
    @Override
    public OpenApiResponse<AccountWithdrawalResponse> withdraw(AccountWithdrawalRequest request) {
        Long jwtUserId = OpenJwtLoginUserHolder.currentUserId();
        if (jwtUserId != null) {
            request.setUserId(jwtUserId);
        }
        if (request.getUserId() == null) {
            throw new IllegalArgumentException("Missing user context");
        }
        return OpenApiResponse.success(accountCommandService.withdraw(request));
    }

    @Override
    public OpenApiResponse<AccountBalanceSheetResponse> getBalanceSheet(Instant snapshotAt) {
        Long jwtUserId = OpenJwtLoginUserHolder.currentUserId();
        if (jwtUserId == null) {
            throw new IllegalArgumentException("Missing user context");
        }
        return OpenApiResponse.success(accountQueryService.getBalanceSheet(jwtUserId, snapshotAt));
    }

    @Override
    public OpenApiResponse<AccountJournalResponse> getAccountJournals(Long accountId,
                                                                      Instant snapshotAt) {
        Long jwtUserId = OpenJwtLoginUserHolder.currentUserId();
        if (jwtUserId == null) {
            throw new IllegalArgumentException("Missing user context");
        }
        if (accountId == null) {
            throw new IllegalArgumentException("Missing accountId");
        }
        return OpenApiResponse.success(accountQueryService.getAccountJournals(jwtUserId, accountId, snapshotAt));
    }

    @Override
    public OpenApiResponse<AccountReferenceJournalResponse> getJournalsByReference(ReferenceType referenceType,
                                                                                   String referenceId) {
        Long jwtUserId = OpenJwtLoginUserHolder.currentUserId();
        if (jwtUserId == null) {
            throw new IllegalArgumentException("Missing user context");
        }
        return OpenApiResponse.success(accountQueryService.getJournalsByReference(jwtUserId, referenceType, referenceId));
    }

    @Override
    public OpenApiResponse<PlatformAccountResponse> getPlatformAccounts(Instant snapshotAt) {
        Long jwtUserId = OpenJwtLoginUserHolder.currentUserId();
        if (jwtUserId == null) {
            throw new IllegalArgumentException("Missing user context");
        }
        return OpenApiResponse.success(accountQueryService.getPlatformAccounts(snapshotAt));
    }

    @Override
    public OpenApiResponse<PlatformAccountJournalResponse> getPlatformAccountJournals(Long accountId,
                                                                                       Instant snapshotAt) {
        Long jwtUserId = OpenJwtLoginUserHolder.currentUserId();
        if (jwtUserId == null) {
            throw new IllegalArgumentException("Missing user context");
        }
        if (accountId == null) {
            throw new IllegalArgumentException("Missing accountId");
        }
        return OpenApiResponse.success(accountQueryService.getPlatformAccountJournals(accountId, snapshotAt));
    }
    
    
}
