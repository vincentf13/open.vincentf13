package open.vincentf13.exchange.account.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.domain.model.AccountBalance;
import open.vincentf13.exchange.account.infra.persistence.po.AccountBalancePO;
import open.vincentf13.exchange.account.infra.persistence.repository.AccountBalanceRepository;
import open.vincentf13.exchange.account.sdk.rest.api.dto.AccountBalanceItem;
import open.vincentf13.exchange.account.sdk.rest.api.dto.AccountBalanceResponse;
import open.vincentf13.exchange.common.sdk.enums.AccountType;
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
    
    private final AccountBalanceRepository accountBalanceRepository;
    
    @Transactional(readOnly = true)
    public AccountBalanceResponse getBalances(@NotNull Long userId,
                                              @NotBlank String asset) {
        AssetSymbol normalizedAsset = AccountBalance.normalizeAsset(asset);
        List<AccountBalance> balances = accountBalanceRepository.findBy(
                Wrappers.lambdaQuery(AccountBalancePO.class)
                        .eq(AccountBalancePO::getUserId, userId)
                        .eq(AccountBalancePO::getAccountType, AccountType.SPOT_MAIN)
                        .eq(AccountBalancePO::getAsset, normalizedAsset)
                                                                     );
        Instant snapshotAt = balances.stream()
                                     .map(AccountBalance::getUpdatedAt)
                                     .filter(Objects::nonNull)
                                     .max(Instant::compareTo)
                                     .orElseGet(() -> null);
        List<AccountBalanceItem> items = OpenObjectMapper.convertList(balances, AccountBalanceItem.class);
        return new AccountBalanceResponse(userId, snapshotAt, items);
    }
}
