package open.vincentf13.exchange.position.sdk.mq.event;


import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PositionTopics {
    POSITION_UPDATED(Names.POSITION_UPDATED, PositionUpdatedEvent.class),
    POSITION_MARGIN_RELEASED(Names.POSITION_MARGIN_RELEASED, PositionMarginReleasedEvent.class),
    POSITION_INVALID_FILL(Names.POSITION_INVALID_FILL, PositionInvalidFillEvent.class);

    private final String topic;
    private final Class<?> eventType;

    public static final class Names {
        public static final String POSITION_UPDATED = "positions.updated";
        public static final String POSITION_MARGIN_RELEASED = "positions.margin-released";
        public static final String POSITION_INVALID_FILL = "positions.invalid-fill";

        private Names() {
        }
    }
}
