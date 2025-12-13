package open.vincentf13.exchange.order.mq.event;

import open.vincentf13.exchange.common.sdk.enums.AssetSymbol;
import open.vincentf13.exchange.common.sdk.enums.OrderSide;
import open.vincentf13.exchange.common.sdk.enums.OrderType;
import open.vincentf13.exchange.common.sdk.enums.PositionIntentType;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderCreatedEvent(
        Long orderId,
        Long userId,
        Long instrumentId,
        OrderSide side,
        OrderType type,
        PositionIntentType intent,
        BigDecimal price,
        BigDecimal quantity,
        String clientOrderId,
        AssetSymbol frozenAsset,
        BigDecimal frozenAmount,
        Instant submittedAt
) {
}
