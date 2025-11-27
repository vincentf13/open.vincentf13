package open.vincentf13.exchange.position.infra;

import open.vincentf13.sdk.core.log.OpenEvent;

/*
 * Position 事件枚舉。
 */
public enum PositionEvent implements OpenEvent {
    POSITION_RESERVED("PositionReserved", "Position reserved"),
    POSITION_RESERVE_REJECTED("PositionReserveRejected", "Position reserve rejected"),
    POSITION_LEVERAGE_UNCHANGED("PositionLeverageUnchanged", "Leverage unchanged"),
    POSITION_LEVERAGE_UPDATED("PositionLeverageUpdated", "Position leverage updated"),
    POSITION_RESERVE_PAYLOAD_INVALID("PositionReservePayloadInvalid", "Invalid PositionReserveRequested payload");

    private final String event;
    private final String message;

    PositionEvent(String event, String message) {
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
