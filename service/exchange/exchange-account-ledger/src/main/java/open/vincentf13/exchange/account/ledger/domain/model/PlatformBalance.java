package open.vincentf13.exchange.account.ledger.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.enums.PlatformAccountCode;
import open.vincentf13.exchange.sdk.common.enums.AssetSymbol;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformBalance {

    private Long id;
    private Long accountId;
    private PlatformAccountCode accountCode;
    private AssetSymbol asset;
    private BigDecimal balance;
    private BigDecimal reserved;
    private Integer version;
    private Long lastEntryId;
    private Instant createdAt;
    private Instant updatedAt;

    public static PlatformBalance createDefault(Long accountId, PlatformAccountCode accountCode, AssetSymbol asset) {
        Instant now = Instant.now();
        return PlatformBalance.builder()
                .accountId(accountId)
                .accountCode(accountCode)
                .asset(asset)
                .balance(BigDecimal.ZERO)
                .reserved(BigDecimal.ZERO)
                .version(0)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public static AssetSymbol normalizeAsset(String asset) {
        return AssetSymbol.fromValue(asset);
    }

    public int safeVersion() {
        return version == null ? 0 : version;
    }
}
