package open.vincentf13.exchange.order.sdk.rest.api.dto;

public enum OrderStatus {
    PENDING,
    SUBMITTED,
    ACCEPTED,
    PARTIAL_FILLED,
    FILLED,
    CANCEL_REQUESTED,
    CANCELLED,
    REJECTED,
    FAILED,
    EXPIRED
}
