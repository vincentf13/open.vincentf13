package open.vincentf13.exchange.order.infra.messaging.mapper;

import open.vincentf13.exchange.order.domain.model.Order;
import open.vincentf13.exchange.order.infra.messaging.event.OrderCancelRequestedEvent;
import open.vincentf13.exchange.order.infra.messaging.event.OrderSubmittedEvent;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.time.Instant;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface OrderEventMessageMapper {

    @Mapping(source = "id", target = "orderId")
    OrderSubmittedEvent toOrderSubmittedEvent(Order order);

    @Mapping(source = "id", target = "orderId")
    OrderCancelRequestedEvent toOrderCancelRequestedEvent(Order order, Instant requestedAt, String reason);
}
