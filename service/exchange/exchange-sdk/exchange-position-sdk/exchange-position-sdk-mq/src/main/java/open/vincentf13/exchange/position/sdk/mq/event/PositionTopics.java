package open.vincentf13.exchange.position.sdk.mq.event;


import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PositionTopics {
    POSITION_UPDATED(Names.POSITION_UPDATED, PositionUpdatedEvent.class),
    POSITION_MARGIN_RELEASED(Names.POSITION_MARGIN_RELEASED, PositionMarginReleasedEvent.class),
    POSITION_CLOSE_TO_OPEN_COMPENSATION(Names.POSITION_CLOSE_TO_OPEN_COMPENSATION, PositionCloseToOpenCompensationEvent.class),
    POSITION_OPEN_TO_CLOSE_COMPENSATION(Names.POSITION_OPEN_TO_CLOSE_COMPENSATION, PositionOpenToCloseCompensationEvent.class);

    private final String topic;
    private final Class<?> eventType;

    public static final class Names {
        public static final String POSITION_UPDATED = "positions.updated";
        public static final String POSITION_MARGIN_RELEASED = "positions.margin-released";
        public static final String POSITION_CLOSE_TO_OPEN_COMPENSATION = "positions.close-to-open-compensation";
        public static final String POSITION_OPEN_TO_CLOSE_COMPENSATION = "positions.open-to-close-compensation";

        private Names() {
        }
    }
}
