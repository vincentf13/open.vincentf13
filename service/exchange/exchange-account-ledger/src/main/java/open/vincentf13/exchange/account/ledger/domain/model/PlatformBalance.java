package open.vincentf13.exchange.account.ledger.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

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
}
