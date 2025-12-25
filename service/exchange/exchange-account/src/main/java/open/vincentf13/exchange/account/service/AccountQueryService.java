package open.vincentf13.exchange.account.service;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.domain.model.UserAccount;
import open.vincentf13.exchange.account.infra.persistence.repository.UserAccountRepository;
import open.vincentf13.exchange.account.sdk.rest.api.dto.AccountBalanceItem;
import open.vincentf13.exchange.account.sdk.rest.api.dto.AccountBalanceResponse;
import open.vincentf13.exchange.account.sdk.rest.api.enums.UserAccountCode;
import open.vincentf13.exchange.common.sdk.enums.AssetSymbol;
import open.vincentf13.sdk.core.object.mapper.OpenObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.Instant;

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
}
