package open.vincentf13.exchange.order.sdk.rest.dto;

import java.time.Instant;
import java.util.List;

public record OrderEventResponse(
        Long orderId,
        Instant snapshotAt,
        List<OrderEventItem> events
) {
}
