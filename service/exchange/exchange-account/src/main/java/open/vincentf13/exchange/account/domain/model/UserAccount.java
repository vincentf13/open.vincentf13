package open.vincentf13.exchange.account.domain.model;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import open.vincentf13.exchange.account.infra.AccountErrorCode;
import open.vincentf13.exchange.account.sdk.rest.api.enums.UserAccountCode;
import open.vincentf13.exchange.account.sdk.rest.api.enums.AccountCategory;
import open.vincentf13.exchange.common.sdk.enums.AssetSymbol;
import open.vincentf13.exchange.common.sdk.enums.Direction;
import open.vincentf13.sdk.core.exception.OpenException;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAccount {
    
    private Long accountId;
    @NotNull
    private Long userId;
    @NotNull
    private UserAccountCode accountCode;
    private String accountName;
    private Long instrumentId;
    @NotNull
    private AccountCategory category;
    @NotNull
    private AssetSymbol asset;
    @NotNull
    private BigDecimal balance;
    @NotNull
    private BigDecimal available;
    @NotNull
    private BigDecimal reserved;
    private Integer version;
    private Instant createdAt;
    private Instant updatedAt;
    
    public static UserAccount createDefault(Long userId,
                                            UserAccountCode accountCode,
                                            Long instrumentId,
                                            AssetSymbol asset) {
        return UserAccount.builder()
                          .userId(userId)
                          .accountCode(accountCode)
                          .accountName(accountCode.getDisplayName())
                          .instrumentId(instrumentId)
                          .category(accountCode.getCategory())
                          .asset(asset)
                          .balance(BigDecimal.ZERO)
                          .available(BigDecimal.ZERO)
                          .reserved(BigDecimal.ZERO)
                          .version(0)
                          .build();
    }
    
    public static AssetSymbol normalizeAsset(String asset) {
        return AssetSymbol.fromValue(asset);
    }
    
    public static AssetSymbol normalizeAsset(AssetSymbol asset) {
        if (asset == null) {
            throw OpenException.of(AccountErrorCode.ASSET_REQUIRED);
        }
        return asset;
    }
    
    public int safeVersion() {
        return version == null ? 0 : version;
    }
    
    public UserAccount apply(Direction direction,
                             BigDecimal amount,
                             boolean affectAvailable) {
        BigDecimal delta = AccountCategory.delta(category, direction, amount);
        BigDecimal newBalance = balance.add(delta);
        BigDecimal newAvailable = affectAvailable ? available.add(delta) : available;
        if (affectAvailable && newAvailable.signum() < 0) {
            throw OpenException.of(AccountErrorCode.INSUFFICIENT_FUNDS);
        }
        return UserAccount.builder()
                          .accountId(accountId)
                          .userId(userId)
                          .accountCode(accountCode)
                          .accountName(accountName)
                          .instrumentId(instrumentId)
                          .category(category)
                          .asset(asset)
                          .balance(newBalance)
                          .available(newAvailable)
                          .reserved(reserved)
                          .version(safeVersion() + 1)
                          .createdAt(createdAt)
                          .updatedAt(Instant.now())
                          .build();
    }
    
    public boolean hasEnoughAvailable(BigDecimal amount) {
        return available.compareTo(amount) >= 0;
    }
    
}
