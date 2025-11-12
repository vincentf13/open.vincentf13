package open.vincentf13.exchange.position.sdk.mq.event;

public final class PositionTopics {
    private PositionTopics() {}

    public static final String POSITION_RESERVE_REQUESTED = "positions.reserve-requested";
    public static final String POSITION_RESERVED = "positions.reserved";
    public static final String POSITION_RESERVE_REJECTED = "positions.reserve-rejected";
}
