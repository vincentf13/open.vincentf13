package open.vincentf13.exchange.position.infra;

import open.vincentf13.sdk.core.exception.OpenErrorCode;

public enum PositionErrorCode implements OpenErrorCode {

    POSITION_NOT_FOUND("Position-404-1001", "Position not found"),
    POSITION_NOT_OWNED("Position-403-1001", "Position does not belong to current user"),
    POSITION_INSUFFICIENT_AVAILABLE("Position-409-1002", "Insufficient available position quantity"),
    POSITION_FLIP_NOT_ALLOWED("Position-409-1003", "Flip not allowed with zero available quantity"),
    POSITION_CONCURRENT_UPDATE("Position-409-1001", "Concurrent update on position");
    
    private final String code;
    private final String message;
    
    PositionErrorCode(String code,
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
