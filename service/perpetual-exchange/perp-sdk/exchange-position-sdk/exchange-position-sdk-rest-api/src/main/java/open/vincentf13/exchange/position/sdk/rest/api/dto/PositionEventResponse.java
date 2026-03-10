package open.vincentf13.exchange.position.sdk.rest.api.dto;

import java.time.Instant;
import java.util.List;

public record PositionEventResponse(
    Long positionId, Instant snapshotAt, List<PositionEventItem> events) {}
