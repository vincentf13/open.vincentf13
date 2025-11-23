package open.vincentf13.exchange.risk.margin.sdk.mq.event;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

public record MarginPreCheckPassedEvent(
        @NotNull Long orderId,
        @NotNull Long userId,
        @NotNull Long instrumentId,
        @NotBlank String asset,
        @NotNull @DecimalMin(value = "0.00000001") BigDecimal requiredMargin,
        @NotNull Instant checkedAt
) {
}
