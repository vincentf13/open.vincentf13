package open.vincentf13.sdk.spring.mvc;

import open.vincentf13.sdk.core.log.OpenEvent;

/*
 * MVC 事件枚舉。
 */
public enum MvcEvent implements OpenEvent {
    HTTP_MESSAGE_UNREADABLE("HttpMessageUnreadable", "Request payload unreadable"),
    OPEN_EXCEPTION("OpenException", "Open exception"),
    UNHANDLED_EXCEPTION("UnhandledException", "Unhandled exception"),
    WRAP_STRING_RESPONSE_FAILED("WrapStringResponseFailed", "Failed to wrap String response body"),
    MVC_REQUEST_FAILED("MvcRequestFailed", "MVC request failed"),
    MVC_REQUEST_COMPLETED("MvcRequestCompleted", "MVC request completed"),
    MVC_REQUEST_DETAIL("MvcRequestDetail", "MVC request detail"),
    CIRCUIT_BREAKER_OPEN("CircuitBreakerOpen", "Circuit breaker is open");
    
    private final String event;
    private final String message;
    
    MvcEvent(String event,
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
