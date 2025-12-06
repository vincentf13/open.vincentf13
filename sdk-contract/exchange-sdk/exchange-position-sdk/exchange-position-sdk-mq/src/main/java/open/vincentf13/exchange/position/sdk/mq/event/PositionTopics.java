package open.vincentf13.exchange.position.sdk.mq.event;


import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PositionTopics {
    POSITION_RESERVE_REQUESTED(Names.POSITION_RESERVE_REQUESTED, PositionReserveRequestedEvent.class),
    POSITION_RESERVED(Names.POSITION_RESERVED, PositionReservedEvent.class),
    POSITION_RESERVE_REJECTED(Names.POSITION_RESERVE_REJECTED, PositionReserveRejectedEvent.class),
    POSITION_UPDATED(Names.POSITION_UPDATED, PositionUpdatedEvent.class),
    POSITION_MARGIN_RELEASED(Names.POSITION_MARGIN_RELEASED, PositionMarginReleasedEvent.class);

    private final String topic;
    private final Class<?> eventType;

    public static final class Names {
        public static final String POSITION_RESERVE_REQUESTED = "positions.reserve-requested";
        public static final String POSITION_RESERVED = "positions.reserve";
        public static final String POSITION_RESERVE_REJECTED = "positions.reserve-rejected";
        public static final String POSITION_UPDATED = "positions.updated";
        public static final String POSITION_MARGIN_RELEASED = "positions.margin-released";

        private Names() {
        }
    }
}
