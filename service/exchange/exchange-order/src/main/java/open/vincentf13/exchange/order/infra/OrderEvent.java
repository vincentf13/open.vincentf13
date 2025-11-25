package open.vincentf13.exchange.order.infra;

import open.vincentf13.sdk.core.log.OpenEvent;

/*
 * Order 事件枚舉。
 */
public enum OrderEvent implements OpenEvent {
    ORDER_STATUS_CONFLICT("OrderStatusConflict", "Order status conflict"),
    ORDER_NOT_FOUND_AFTER_RESERVE("OrderNotFoundAfterReserve", "Order updated but not found for event publish"),
    ORDER_POSITION_RESERVED("OrderPositionReserved", "Position reserved for order"),
    ORDER_POSITION_RESERVE_REJECTED("OrderPositionReserveRejected", "Position reserve rejected"),
    ORDER_NOT_FOUND_AFTER_RESERVE_REJECT("OrderNotFoundAfterReserveReject", "Order updated but not found for reject log"),
    ORDER_DUPLICATE_INSERT("OrderDuplicateInsert", "Duplicate order insert detected"),
    ORDER_FUNDS_FROZEN_SKIP_NOT_FOUND("OrderFundsFrozenSkipNotFound", "Skip funds frozen event, order not found"),
    ORDER_FUNDS_FROZEN_LOCK_CONFLICT("OrderFundsFrozenLockConflict", "Optimistic lock conflict while marking order accepted"),
    ORDER_MARK_ACCEPTED("OrderMarkAccepted", "Order marked ACCEPTED after funds frozen"),
    ORDER_FAILURE_SKIP_NOT_FOUND("OrderFailureSkipNotFound", "Skip failure event, order not found"),
    ORDER_FAILURE_OPTIMISTIC_LOCK("OrderFailureOptimisticLock", "Optimistic lock conflict while marking order failed"),
    ORDER_MARK_FAILED("OrderMarkFailed", "Order marked FAILED");

    private final String event;
    private final String message;

    OrderEvent(String event, String message) {
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
