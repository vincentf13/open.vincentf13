package open.vincentf13.exchange.order.infra;

import open.vincentf13.sdk.core.log.OpenEvent;

/*
 * Order 事件枚舉。
 */
public enum OrderEvent implements OpenEvent {
    ORDER_STATUS_CONFLICT("OrderStatusConflict", "Order status conflict"),
    ORDER_NOT_FOUND_AFTER_RESERVE("OrderNotFoundAfterReserve", "Order updated but not found for event publish"),
    ORDER_DUPLICATE_INSERT("OrderDuplicateInsert", "Duplicate order insert detected"),
    ORDER_FAILURE_SKIP_NOT_FOUND("OrderFailureSkipNotFound", "Skip failure event, order not found"),
    ORDER_FAILURE_OPTIMISTIC_LOCK("OrderFailureOptimisticLock", "Optimistic lock conflict while marking order rejected"),
    ORDER_MARK_FAILED("OrderMarkFailed", "Order marked REJECTED"),
    ORDER_RISK_PAYLOAD_INVALID("OrderRiskPayloadInvalid", "Invalid risk event payload"),
    ORDER_LEDGER_PAYLOAD_INVALID("OrderLedgerPayloadInvalid", "Invalid ledger event payload");
    
    private final String event;
    private final String message;
    
    OrderEvent(String event,
               String message) {
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
