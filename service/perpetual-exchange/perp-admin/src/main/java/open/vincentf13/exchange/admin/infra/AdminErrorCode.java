package open.vincentf13.exchange.admin.infra;

import open.vincentf13.sdk.core.exception.OpenErrorCode;

public enum AdminErrorCode implements OpenErrorCode {
    INSTRUMENT_NOT_FOUND("Instrument not found");
    
    private final String message;
    
    AdminErrorCode(String message) {
        this.message = message;
    }
    
    @Override
    public String code() {
        return name();
    }
    
    @Override
    public String message() {
        return message;
    }
}
