package open.vincentf13.exchange.account.ledger.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformBalance {

    private Long id;
    private Long accountId;
    private String accountCode;
    private String asset;
    private BigDecimal balance;
    private BigDecimal reserved;
    private Integer version;
    private Long lastEntryId;
    private Instant createdAt;
    private Instant updatedAt;

    public static PlatformBalance createDefault(Long accountId, String accountCode, String asset) {
        Instant now = Instant.now();
        return PlatformBalance.builder()
                .accountId(accountId)
                .accountCode(accountCode)
                .asset(normalizeAsset(asset))
                .balance(BigDecimal.ZERO)
                .reserved(BigDecimal.ZERO)
                .version(0)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public static String normalizeAsset(String asset) {
        if (asset == null) {
            throw new IllegalArgumentException("asset is required");
        }
        return asset.toUpperCase(Locale.ROOT);
    }
}
