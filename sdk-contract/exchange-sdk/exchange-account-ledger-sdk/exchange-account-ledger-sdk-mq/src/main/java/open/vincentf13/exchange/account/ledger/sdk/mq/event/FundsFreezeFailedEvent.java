package open.vincentf13.exchange.account.ledger.sdk.mq.event;

import jakarta.validation.constraints.NotBlank;

public record FundsFreezeFailedEvent(
        @org.jetbrains.annotations.NotNull Long orderId,
        @NotBlank String reason
) {
}
