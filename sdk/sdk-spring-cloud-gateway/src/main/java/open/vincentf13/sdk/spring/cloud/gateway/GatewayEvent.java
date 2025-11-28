package open.vincentf13.sdk.spring.cloud.gateway;

import open.vincentf13.sdk.core.log.OpenEvent;

/*
  Gateway 事件枚舉。
 */
public enum GatewayEvent implements OpenEvent {
    REQUEST("GatewayRequest", "Forwarding request"),
    REQUEST_HEADER("GatewayRequestHeader", "Request header"),
    ROUTE("GatewayRoute", "Resolved route"),
    FORWARD_URL("GatewayForwardUrl", "Forward URL"),
    SERVICE_INSTANCE("GatewayServiceInstance", "Service instance resolved"),
    SERVICE_INSTANCE_PENDING("GatewayServiceInstancePending", "Service instance not yet resolved"),
    RESPONSE("GatewayResponse", "Response completed"),
    FORWARD_URL_RESOLVED("GatewayForwardUrlResolved", "Forward URL resolved"),
    SERVICE_INSTANCE_RESOLVED("GatewayServiceInstanceResolved", "Service instance resolved after filter"),
    FORWARD_FAILED("GatewayForwardFailed", "Forwarding failed"),
    RESPONSE_HEADER("GatewayResponseHeader", "Response header"),
    JWT_INVALID("GatewayJwtInvalid", "JWT access jwtToken validation failed"),
    JWT_SESSION_INACTIVE("GatewayJwtSessionInactive", "Session inactive, rejecting request"),
    JWT_UNAUTHORIZED("GatewayJwtUnauthorized", "Request rejected");

    private final String event;
    private final String message;

    GatewayEvent(String event, String message) {
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
