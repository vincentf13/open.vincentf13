package open.vincentf13.exchange.risk.margin.sdk.mq.event;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import open.vincentf13.exchange.common.sdk.constants.ValidationConstant;

import java.math.BigDecimal;
import java.time.Instant;

public record MarginPreCheckPassedEvent(
        @NotNull Long orderId,
        @NotNull Long userId,
        @NotNull Long instrumentId,
        @NotBlank String asset,
        @NotNull @DecimalMin(value = ValidationConstant.Names.AMOUNT_MIN) BigDecimal requiredMargin,
        @NotNull Instant checkedAt
) {
}
