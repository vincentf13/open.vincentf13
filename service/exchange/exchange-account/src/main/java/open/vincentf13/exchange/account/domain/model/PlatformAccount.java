package open.vincentf13.exchange.account.domain.model;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import open.vincentf13.exchange.account.sdk.rest.api.enums.AccountCategory;
import open.vincentf13.exchange.account.sdk.rest.api.enums.PlatformAccountCode;
import open.vincentf13.exchange.common.sdk.enums.AssetSymbol;
import open.vincentf13.exchange.common.sdk.enums.Direction;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformAccount {
    
    private Long accountId;
    @NotNull
    private PlatformAccountCode accountCode;
    private String accountName;
    @NotNull
    private AccountCategory category;
    @NotNull
    private AssetSymbol asset;
    @NotNull
    private BigDecimal balance;
    private Integer version;
    private Instant createdAt;
    private Instant updatedAt;

    public static PlatformAccount createDefault(PlatformAccountCode accountCode,
                                                AssetSymbol asset) {
        return PlatformAccount.builder()
                              .accountCode(accountCode)
                              .accountName(accountCode.getDisplayName())
                              .category(accountCode.getCategory())
                              .asset(asset)
                              .balance(BigDecimal.ZERO)
                              .version(0)
                              .build();
    }
    
    public PlatformAccount apply(Direction direction,
                                 BigDecimal amount) {
        BigDecimal delta = AccountCategory.delta(category, direction, amount);
        return PlatformAccount.builder()
                              .accountId(accountId)
                              .accountCode(accountCode)
                              .accountName(accountName)
                              .category(category)
                              .asset(asset)
                              .balance(balance.add(delta))
                              .version(safeVersion() + 1)
                              .createdAt(createdAt)
                              .build();
    }
    
    public int safeVersion() {
        return version == null ? 0 : version;
    }
    
}
