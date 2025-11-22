package open.vincentf13.exchange.account.ledger.controller;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.LedgerAccountApi;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.dto.LedgerBalanceResponse;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.dto.LedgerDepositRequest;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.dto.LedgerDepositResponse;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.dto.LedgerWithdrawalRequest;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.dto.LedgerWithdrawalResponse;
import open.vincentf13.exchange.account.ledger.service.LedgerAccountCommandService;
import open.vincentf13.exchange.account.ledger.service.LedgerAccountQueryService;
import open.vincentf13.sdk.auth.jwt.OpenJwtLoginUserInfo;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ledger")
@RequiredArgsConstructor
public class LedgerAccountController implements LedgerAccountApi {

    private final LedgerAccountQueryService ledgerAccountQueryService;
    private final LedgerAccountCommandService ledgerAccountCommandService;

    @Override
    public OpenApiResponse<LedgerBalanceResponse> getBalances(String asset, Long userId) {
        Long effectiveUserId = userId != null
                ? userId
                : OpenJwtLoginUserInfo.currentUserIdOrThrow(() -> new IllegalArgumentException("Missing user context"));
        return OpenApiResponse.success(ledgerAccountQueryService.getBalances(effectiveUserId, asset));
    }

    @Override
    public OpenApiResponse<LedgerDepositResponse> deposit(LedgerDepositRequest request) {
        return OpenApiResponse.success(ledgerAccountCommandService.deposit(request));
    }

    @Override
    public OpenApiResponse<LedgerWithdrawalResponse> withdraw(LedgerWithdrawalRequest request) {
        return OpenApiResponse.success(ledgerAccountCommandService.withdraw(request));
    }
}
