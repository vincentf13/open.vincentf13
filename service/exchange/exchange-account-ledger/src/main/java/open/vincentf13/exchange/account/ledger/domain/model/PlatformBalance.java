package open.vincentf13.exchange.account.ledger.domain.model;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import open.vincentf13.exchange.common.sdk.enums.PlatformAccountCode;
import open.vincentf13.exchange.common.sdk.constants.ValidationConstant;
import open.vincentf13.exchange.common.sdk.enums.AssetSymbol;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformBalance {
    
    private Long id;
    private Long accountId;
    @NotNull
    private PlatformAccountCode accountCode;
    @NotNull
    private AssetSymbol asset;
    @NotNull
    @DecimalMin(value = ValidationConstant.Names.NON_NEGATIVE, inclusive = true)
    private BigDecimal balance;
    @NotNull
    @DecimalMin(value = ValidationConstant.Names.NON_NEGATIVE, inclusive = true)
    private BigDecimal reserved;
    private Integer version;
    private Long lastEntryId;
    private Instant createdAt;
    private Instant updatedAt;
    
    public static PlatformBalance createDefault(Long accountId,
                                                PlatformAccountCode accountCode,
                                                AssetSymbol asset) {
        return PlatformBalance.builder()
                              .accountId(accountId)
                              .accountCode(accountCode)
                              .asset(asset)
                              .balance(BigDecimal.ZERO)
                              .reserved(BigDecimal.ZERO)
                              .version(0)
                              .build();
    }
    
    public static AssetSymbol normalizeAsset(String asset) {
        return AssetSymbol.fromValue(asset);
    }
    
    public int safeVersion() {
        return version == null ? 0 : version;
    }
}
