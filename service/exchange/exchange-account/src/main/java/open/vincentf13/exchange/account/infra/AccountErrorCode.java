package open.vincentf13.exchange.account.infra;

import open.vincentf13.sdk.core.exception.OpenErrorCode;

public enum AccountErrorCode implements OpenErrorCode {
    
    ASSET_REQUIRED("Account-400-1001", "Asset is required"),
    ORDER_INTENT_NULL("Account-400-1002", "Order intent is null"),
    UNSUPPORTED_ORDER_INTENT("Account-400-1003", "Unsupported order intent"),
    INVALID_AMOUNT("Account-400-1004", "Invalid amount"),
    INVALID_EVENT("Account-400-1005", "Invalid event"),
    
    FREEZE_ENTRY_NOT_FOUND("Account-404-1001", "Freeze entry not found"),
    ACCOUNT_BALANCE_NOT_FOUND("Account-404-1002", "Account balance not found"),
    PLATFORM_BALANCE_NOT_FOUND("Account-404-1003", "Platform balance not found"),
    
    OPTIMISTIC_LOCK_FAILURE("Account-409-1001", "Failed to update due to concurrent modification"),
    DUPLICATE_REQUEST("Account-409-1002", "Duplicate request"),
    
    INSUFFICIENT_FUNDS("Account-422-1001", "Insufficient funds"),
    INSUFFICIENT_RESERVED_BALANCE("Account-422-1002", "Insufficient reserved balance");
    
    private final String code;
    private final String message;
    
    AccountErrorCode(String code,
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
