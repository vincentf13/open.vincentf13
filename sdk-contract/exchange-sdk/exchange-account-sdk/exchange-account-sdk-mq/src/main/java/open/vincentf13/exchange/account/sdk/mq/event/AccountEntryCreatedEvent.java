package open.vincentf13.exchange.account.sdk.mq.event;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import open.vincentf13.exchange.common.sdk.enums.AssetSymbol;
import open.vincentf13.exchange.common.sdk.enums.EntryType;
import open.vincentf13.exchange.common.sdk.enums.ReferenceType;
import open.vincentf13.exchange.common.sdk.constants.ValidationConstant;

import java.math.BigDecimal;
import java.time.Instant;

public record AccountEntryCreatedEvent(
        @NotNull Long entryId,
        Long userId,
        @NotNull AssetSymbol asset,
        @NotNull BigDecimal deltaBalance,
        @NotNull @DecimalMin(value = ValidationConstant.Names.AMOUNT_MIN) BigDecimal balanceAfter,
        @NotNull ReferenceType referenceType,
        @NotNull String referenceId,
        @NotNull EntryType entryType,
        @NotNull Long instrumentId,
        @NotNull Instant eventTime
) {
}
