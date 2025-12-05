package open.vincentf13.exchange.account.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.domain.model.UserAccount;
import open.vincentf13.exchange.account.infra.persistence.po.UserAccountPO;
import open.vincentf13.exchange.account.infra.persistence.repository.UserAccountRepository;
import open.vincentf13.exchange.account.sdk.rest.api.enums.AccountCategory;
import open.vincentf13.exchange.account.sdk.rest.api.enums.UserAccountCode;
import open.vincentf13.exchange.account.sdk.rest.api.dto.AccountBalanceItem;
import open.vincentf13.exchange.account.sdk.rest.api.dto.AccountBalanceResponse;
import open.vincentf13.exchange.common.sdk.enums.AssetSymbol;
import open.vincentf13.sdk.core.object.mapper.OpenObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Validated
public class AccountQueryService {
    
    private final UserAccountRepository userAccountRepository;
    
    @Transactional(readOnly = true)
    public AccountBalanceResponse getBalances(@NotNull Long userId,
                                              @NotBlank String asset) {
        AssetSymbol normalizedAsset = UserAccount.normalizeAsset(asset);
        List<UserAccount> balances = userAccountRepository.findBy(
                Wrappers.lambdaQuery(UserAccountPO.class)
                        .eq(UserAccountPO::getUserId, userId)
                        .eq(UserAccountPO::getAccountCode, UserAccountCode.SPOT)
                        .eq(UserAccountPO::getCategory, AccountCategory.ASSET)
                        .eq(UserAccountPO::getAsset, normalizedAsset)
                                                                    );
        Instant snapshotAt = balances.stream()
                                     .map(UserAccount::getUpdatedAt)
                                     .filter(Objects::nonNull)
                                     .max(Instant::compareTo)
                                     .orElse(null);
        List<AccountBalanceItem> items = OpenObjectMapper.convertList(balances, AccountBalanceItem.class);
        return new AccountBalanceResponse(userId, snapshotAt, items);
    }
}
