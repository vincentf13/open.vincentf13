package open.vincentf13.exchange.account.ledger.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.ledger.domain.model.LedgerBalance;
import open.vincentf13.exchange.account.ledger.infra.persistence.po.LedgerBalancePO;
import open.vincentf13.exchange.account.ledger.infra.persistence.repository.LedgerBalanceRepository;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.dto.LedgerBalanceItem;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.dto.LedgerBalanceResponse;
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
public class LedgerBalanceQueryService {
    
    private final LedgerBalanceRepository ledgerBalanceRepository;
    
    @Transactional(readOnly = true)
    public LedgerBalanceResponse getBalances(@NotNull Long userId,
                                             @NotBlank String asset) {
        AssetSymbol normalizedAsset = LedgerBalance.normalizeAsset(asset);
        List<LedgerBalance> balances = ledgerBalanceRepository.findBy(
                Wrappers.lambdaQuery(LedgerBalancePO.class)
                        .eq(LedgerBalancePO::getUserId, userId)
                        .eq(LedgerBalancePO::getAccountType, AccountType.SPOT_MAIN)
                        .eq(LedgerBalancePO::getAsset, normalizedAsset)
                                                                     );
        Instant snapshotAt = balances.stream()
                                     .map(LedgerBalance::getUpdatedAt)
                                     .filter(Objects::nonNull)
                                     .max(Instant::compareTo)
                                     .orElseGet(() -> null);
        List<LedgerBalanceItem> items = OpenObjectMapper.convertList(balances, LedgerBalanceItem.class);
        return new LedgerBalanceResponse(userId, snapshotAt, items);
    }
}
