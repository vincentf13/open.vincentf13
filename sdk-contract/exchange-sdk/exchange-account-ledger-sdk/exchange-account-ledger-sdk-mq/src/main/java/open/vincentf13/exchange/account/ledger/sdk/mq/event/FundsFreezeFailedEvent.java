package open.vincentf13.exchange.account.ledger.sdk.mq.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record FundsFreezeFailedEvent(
        @NotNull Long orderId,
        @NotBlank String reason
) {
}
