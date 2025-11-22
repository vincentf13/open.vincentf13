package open.vincentf13.exchange.account.ledger.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.enums.AccountType;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.enums.AssetSymbol;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerBalance {

    private Long id;
    private Long accountId;
    private Long userId;
    private AccountType accountType;
    private Long instrumentId;
    private AssetSymbol asset;
    private BigDecimal balance;
    private BigDecimal available;
    private BigDecimal reserved;
    private BigDecimal totalDeposited;
    private BigDecimal totalWithdrawn;
    private BigDecimal totalPnl;
    private Integer version;
    private Long lastEntryId;
    private Instant createdAt;
    private Instant updatedAt;

    public static LedgerBalance createDefault(Long userId,
                                              AccountType accountType,
                                              Long instrumentId,
                                              AssetSymbol asset) {
        Instant now = Instant.now();
        return LedgerBalance.builder()
                .userId(userId)
                .accountType(accountType)
                .instrumentId(instrumentId)
                .asset(asset)
                .balance(BigDecimal.ZERO)
                .available(BigDecimal.ZERO)
                .reserved(BigDecimal.ZERO)
                .totalDeposited(BigDecimal.ZERO)
                .totalWithdrawn(BigDecimal.ZERO)
                .totalPnl(BigDecimal.ZERO)
                .version(0)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
    public static AssetSymbol normalizeAsset(String asset) {
        return AssetSymbol.fromValue(asset);
    }

    public static AssetSymbol normalizeAsset(AssetSymbol asset) {
        if (asset == null) {
            throw new IllegalArgumentException("asset is required");
        }
        return asset;
    }
}
