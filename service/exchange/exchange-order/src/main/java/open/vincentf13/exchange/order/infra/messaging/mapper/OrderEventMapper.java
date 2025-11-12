package open.vincentf13.exchange.order.infra.messaging.mapper;

import open.vincentf13.exchange.order.domain.model.Order;
import open.vincentf13.exchange.order.mq.event.OrderCancelRequestedEvent;
import open.vincentf13.exchange.order.mq.event.OrderSubmittedEvent;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.time.Instant;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface OrderEventMapper {

    @Mapping(source = "order.id", target = "orderId")
    OrderSubmittedEvent toOrderSubmittedEvent(Order order);

    @Mapping(source = "order.id", target = "orderId")
    OrderCancelRequestedEvent toOrderCancelRequestedEvent(Order order, Instant requestedAt, String reason);
}
