package open.vincentf13.exchange.position.domain.model;

import open.vincentf13.exchange.order.sdk.rest.api.dto.OrderSide;

public enum PositionSide {
    LONG,
    SHORT;

    public static PositionSide fromOrderSide(OrderSide orderSide) {
        if (orderSide == null) {
            return null;
        }
        return orderSide == OrderSide.BUY ? LONG : SHORT;
    }

    public boolean isSameDirection(OrderSide orderSide) {
        PositionSide other = fromOrderSide(orderSide);
        return other == null || other == this;
    }

    public OrderSide toOrderSide() {
        return this == LONG ? OrderSide.BUY : OrderSide.SELL;
    }
}
