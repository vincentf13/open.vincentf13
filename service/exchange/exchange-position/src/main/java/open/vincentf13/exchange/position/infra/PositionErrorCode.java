package open.vincentf13.exchange.position.infra;

import open.vincentf13.sdk.core.exception.OpenErrorCode;

public enum PositionErrorCode implements OpenErrorCode {

    POSITION_NOT_FOUND("Position-404-1001", "Position not found"),
    INVALID_LEVERAGE_REQUEST("Position-400-1001", "Invalid leverage request"),
    LEVERAGE_PRECHECK_FAILED("Position-422-1001", "Leverage pre-check failed");

    private final String code;
    private final String message;

    PositionErrorCode(String code, String message) {
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
