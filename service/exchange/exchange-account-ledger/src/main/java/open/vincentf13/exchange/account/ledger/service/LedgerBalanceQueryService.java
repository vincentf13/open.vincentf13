package open.vincentf13.exchange.account.ledger.service;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.ledger.domain.model.LedgerBalance;
import open.vincentf13.exchange.account.ledger.infra.persistence.repository.LedgerBalanceRepository;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.dto.LedgerBalanceItem;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.dto.LedgerBalanceResponse;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.enums.AccountType;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.enums.AssetSymbol;
import open.vincentf13.sdk.core.OpenMapstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class LedgerBalanceQueryService {

    private final LedgerBalanceRepository ledgerBalanceRepository;

    @Transactional(readOnly = true)
    public LedgerBalanceResponse getBalances(Long userId, String asset) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        if (!StringUtils.hasText(asset)) {
            throw new IllegalArgumentException("asset is required");
        }
        AssetSymbol normalizedAsset = LedgerBalance.normalizeAsset(asset);
        List<LedgerBalance> balances = ledgerBalanceRepository.findBy(LedgerBalance.builder()
                .userId(userId)
                .accountType(AccountType.SPOT_MAIN)
                .asset(normalizedAsset)
                .build());
        Instant snapshotAt = balances.stream()
                .map(LedgerBalance::getUpdatedAt)
                .filter(Objects::nonNull)
                .max(Instant::compareTo)
                .orElseGet(() -> null);
        List<LedgerBalanceItem> items = OpenMapstruct.mapList(balances, LedgerBalanceItem.class);
        return new LedgerBalanceResponse(userId, snapshotAt, items);
    }
}
