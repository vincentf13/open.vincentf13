package open.vincentf13.exchange.position.sdk.rest.api.dto;

public enum PositionIntentType {
    INCREASE,
    REDUCE,
    CLOSE;

    public boolean requiresPositionReservation() {
        return this == REDUCE || this == CLOSE;
    }
}
