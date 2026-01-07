package open.vincentf13.exchange.account.domain.model;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import open.vincentf13.exchange.account.sdk.rest.api.enums.AccountCategory;
import open.vincentf13.exchange.account.sdk.rest.api.enums.ReferenceType;
import open.vincentf13.exchange.common.sdk.enums.AssetSymbol;
import open.vincentf13.exchange.common.sdk.enums.Direction;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserJournal {
    
    @NotNull
    private Long journalId;
    @NotNull
    private Long userId;
    @NotNull
    private Long accountId;
    @NotNull
    private AccountCategory category;
    @NotNull
    private AssetSymbol asset;
    @NotNull
    private BigDecimal amount;
    @NotNull
    private Direction direction;
    @NotNull
    private BigDecimal balanceAfter;
    @NotNull
    private ReferenceType referenceType;
    @NotNull
    private String referenceId;
    @NotNull
    private Integer seq;
    private String description;
    @NotNull
    private Instant eventTime;
    private Instant createdAt;
}
