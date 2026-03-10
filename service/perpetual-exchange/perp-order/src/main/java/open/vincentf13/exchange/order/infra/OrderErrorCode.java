package open.vincentf13.exchange.order.infra;

import open.vincentf13.sdk.core.exception.OpenErrorCode;

public enum OrderErrorCode implements OpenErrorCode {
    ORDER_ALREADY_EXISTS("Order-409-1001", "Order already exists"),
    ORDER_NOT_FOUND("Order-404-1001", "Order not found"),
    ORDER_NOT_OWNED("Order-403-1001", "Order does not belong to current user"),
    ORDER_STATUS_NOT_CANCELABLE("Order-409-1002", "Order status does not allow cancel"),
    ORDER_VALIDATION_FAILED("Order-400-1001", "Order validation failed"),
    ORDER_STATE_CONFLICT("Order-409-1003", "Order state conflict");
    
    private final String code;
    private final String message;
    
    OrderErrorCode(String code,
                   String message) {
        this.code = code;
        this.message = message;
    }
    
    @Override
    public String code() {
        return code;
    }
    
    @Override
    public String message() {
        return message;
    }
}
