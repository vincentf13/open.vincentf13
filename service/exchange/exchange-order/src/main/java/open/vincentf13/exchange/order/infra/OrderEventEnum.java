package open.vincentf13.exchange.order.infra;

import open.vincentf13.sdk.core.log.OpenEvent;

/*
 * Order 事件枚舉。
 */
public enum OrderEventEnum implements OpenEvent {
    ORDER_STATUS_CONFLICT("OrderStatusConflict", "Order status conflict"),
    ORDER_NOT_FOUND_AFTER_RESERVE("OrderNotFoundAfterReserve", "Order updated but not found for event publish"),
    ORDER_POSITION_RESERVED("OrderPositionReserved", "Position reserved for order"),
    ORDER_POSITION_RESERVE_REJECTED("OrderPositionReserveRejected", "Position reserve rejected"),
    ORDER_NOT_FOUND_AFTER_RESERVE_REJECT("OrderNotFoundAfterReserveReject", "Order updated but not found for reject log"),
    ORDER_DUPLICATE_INSERT("OrderDuplicateInsert", "Duplicate order insert detected");

    private final String event;
    private final String message;

    OrderEventEnum(String event, String message) {
        this.event = event;
        this.message = message;
    }

    @Override
    public String event() {
        return event;
    }

    @Override
    public String message() {
        return message;
    }
}
