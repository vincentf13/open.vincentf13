package open.vincentf13.exchange.order.mq.event;

import java.math.BigDecimal;
import java.time.Instant;
import open.vincentf13.exchange.common.sdk.enums.OrderSide;
import open.vincentf13.exchange.common.sdk.enums.OrderType;
import open.vincentf13.exchange.common.sdk.enums.PositionIntentType;
import open.vincentf13.exchange.common.sdk.enums.TradeType;

public record OrderCreatedEvent(
    Long orderId,
    Long userId,
    Long instrumentId,
    OrderSide side,
    OrderType type,
    PositionIntentType intent,
    TradeType tradeType,
    BigDecimal price,
    BigDecimal quantity,
    String clientOrderId,
    Instant submittedAt) {}
