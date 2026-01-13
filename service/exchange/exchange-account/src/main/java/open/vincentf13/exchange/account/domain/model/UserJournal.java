package open.vincentf13.exchange.account.domain.model;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import open.vincentf13.exchange.account.sdk.rest.api.enums.AccountCategory;
import open.vincentf13.exchange.account.sdk.rest.api.enums.ReferenceType;
import open.vincentf13.exchange.common.sdk.enums.AssetSymbol;
import open.vincentf13.exchange.common.sdk.enums.Direction;
import open.vincentf13.sdk.core.validator.Id;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserJournal {

  @NotNull(groups = Id.class)
  private Long journalId;

  @NotNull private Long userId;
  @NotNull private Long accountId;
  @NotNull private AccountCategory category;
  @NotNull private AssetSymbol asset;
  @NotNull private BigDecimal amount;
  @NotNull private Direction direction;
  @NotNull private BigDecimal balanceAfter;
  @NotNull private ReferenceType referenceType;
  @NotNull private String referenceId;
  @NotNull private Integer seq;
  private String description;
  @NotNull private Instant eventTime;
  private Instant createdAt;

  public static UserJournal create(
      Long journalId,
      UserAccount account,
      Direction direction,
      BigDecimal amount,
      ReferenceType referenceType,
      String referenceId,
      Integer seq,
      String description,
      Instant eventTime) {
    return UserJournal.builder()
        .journalId(journalId)
        .userId(account.getUserId())
        .accountId(account.getAccountId())
        .category(account.getCategory())
        .asset(account.getAsset())
        .amount(amount)
        .direction(direction)
        .balanceAfter(account.getBalance())
        .referenceType(referenceType)
        .referenceId(referenceId)
        .seq(seq)
        .description(description)
        .eventTime(eventTime)
        .build();
  }
}
