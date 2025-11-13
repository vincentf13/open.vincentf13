package open.vincentf13.exchange.order.domain.model;

/**
 * Enumerates all order lifecycle events persisted to order_events table.
 */
public enum OrderEventType {
    ORDER_CREATED,
    ORDER_SUBMITTED,
    RISK_CHECK_PASSED,
    RISK_CHECK_FAILED,
    FUNDS_FROZEN,
    FUNDS_FROZEN_FAILED,
    ORDER_ACCEPTED,
    ORDER_PLACED,
    ORDER_PARTIALLY_FILLED,
    ORDER_FILLED,
    ORDER_CANCEL_REQUESTED,
    ORDER_CANCELLED,
    ORDER_REJECTED,
    ORDER_EXPIRED
}
