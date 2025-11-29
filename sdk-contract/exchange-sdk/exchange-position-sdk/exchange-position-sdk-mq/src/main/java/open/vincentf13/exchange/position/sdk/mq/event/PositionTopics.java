package open.vincentf13.exchange.position.sdk.mq.event;

public final class PositionTopics {
    public static final String POSITION_RESERVED = "positions.reserved";
    public static final String POSITION_RESERVE_REJECTED = "positions.reserve-rejected";
    public static final String POSITION_UPDATED = "positions.updated";
    public static final String POSITION_CLOSED = "positions.closed";

    private PositionTopics() {
    }
}
