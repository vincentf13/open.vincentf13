package open.vincentf13.exchange.account.ledger.domain.model;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import open.vincentf13.exchange.account.ledger.infra.LedgerErrorCode;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.enums.AccountType;
import open.vincentf13.exchange.common.sdk.constants.ValidationConstant;
import open.vincentf13.exchange.common.sdk.enums.AssetSymbol;
import open.vincentf13.sdk.core.exception.OpenException;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerBalance {
    
    private Long id;
    private Long accountId;
    @NotNull
    private Long userId;
    @NotNull
    private AccountType accountType;
    private Long instrumentId;
    @NotNull
    private AssetSymbol asset;
    @NotNull
    @DecimalMin(value = ValidationConstant.Names.NON_NEGATIVE, inclusive = true)
    private BigDecimal balance;
    @NotNull
    @DecimalMin(value = ValidationConstant.Names.NON_NEGATIVE, inclusive = true)
    private BigDecimal available;
    @NotNull
    @DecimalMin(value = ValidationConstant.Names.NON_NEGATIVE, inclusive = true)
    private BigDecimal reserved;
    @NotNull
    @DecimalMin(value = ValidationConstant.Names.NON_NEGATIVE, inclusive = true)
    private BigDecimal totalDeposited;
    @NotNull
    @DecimalMin(value = ValidationConstant.Names.NON_NEGATIVE, inclusive = true)
    private BigDecimal totalWithdrawn;
    @NotNull
    @DecimalMin(value = ValidationConstant.Names.NON_NEGATIVE, inclusive = true)
    private BigDecimal totalPnl;
    private Integer version;
    private Long lastEntryId;
    private Instant createdAt;
    private Instant updatedAt;
    
    public static LedgerBalance createDefault(Long userId,
                                              AccountType accountType,
                                              Long instrumentId,
                                              AssetSymbol asset) {
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
                            .build();
    }
    
    public static AssetSymbol normalizeAsset(String asset) {
        return AssetSymbol.fromValue(asset);
    }
    
    public static AssetSymbol normalizeAsset(AssetSymbol asset) {
        if (asset == null) {
            throw OpenException.of(LedgerErrorCode.ASSET_REQUIRED);
        }
        return asset;
    }
    
    public int safeVersion() {
        return version == null ? 0 : version;
    }
}
