package open.vincentf13.exchange.risk.margin.sdk.mq.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MarginPreCheckFailedEvent(
        @NotNull Long orderId,
        @NotBlank String reason
) {
}
