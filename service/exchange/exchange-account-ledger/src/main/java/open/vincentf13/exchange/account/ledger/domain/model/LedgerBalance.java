package open.vincentf13.exchange.account.ledger.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.dto.LedgerBalanceAccountType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerBalance {

    private Long id;
    private Long accountId;
    private Long userId;
    private LedgerBalanceAccountType accountType;
    private Long instrumentId;
    private String asset;
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
                                              LedgerBalanceAccountType accountType,
                                              Long instrumentId,
                                              String asset) {
        Instant now = Instant.now();
        return LedgerBalance.builder()
                .userId(userId)
                .accountType(accountType)
                .instrumentId(instrumentId)
                .asset(normalizeAsset(asset))
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
    public static String normalizeAsset(String asset) {
        if (asset == null) {
            throw new IllegalArgumentException("asset is required");
        }
        return asset.toUpperCase(Locale.ROOT);
    }
}
