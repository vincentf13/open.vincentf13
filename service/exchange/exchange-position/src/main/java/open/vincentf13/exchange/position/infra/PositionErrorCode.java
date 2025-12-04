package open.vincentf13.exchange.position.infra;

import open.vincentf13.sdk.core.exception.OpenErrorCode;

public enum PositionErrorCode implements OpenErrorCode {

    POSITION_NOT_FOUND("Position-404-1001", "Position not found"),
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
