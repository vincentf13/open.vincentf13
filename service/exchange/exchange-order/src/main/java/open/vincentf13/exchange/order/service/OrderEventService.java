package open.vincentf13.exchange.order.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.exchange.order.domain.model.Order;
import open.vincentf13.exchange.order.domain.model.OrderEvent;
import open.vincentf13.exchange.order.domain.model.OrderEventType;
import open.vincentf13.exchange.order.domain.model.OrderErrorCode;
import open.vincentf13.exchange.order.infra.persistence.repository.OrderEventRepository;
import open.vincentf13.exchange.order.messaging.payload.OrderCancelRequestedPayload;
import open.vincentf13.exchange.order.messaging.payload.OrderCreatedPayload;
import open.vincentf13.exchange.order.messaging.payload.OrderSubmittedPayload;
import open.vincentf13.sdk.core.OpenObjectMapper;
import open.vincentf13.sdk.core.exception.OpenServiceException;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderEventService {

    private final OrderEventRepository orderEventRepository;

    public void recordOrderCreated(Order order) {
        persistEvent(order, OrderEventType.ORDER_CREATED,
                new OrderCreatedPayload(
                        order.getId(),
                        order.getUserId(),
                        order.getInstrumentId(),
                        order.getSide(),
                        order.getType(),
                        order.getTimeInForce(),
                        order.getPrice(),
                        order.getStopPrice(),
                        order.getQuantity(),
                        order.getSource(),
                        order.getClientOrderId(),
                        order.getCreatedAt()
                ),
                userActor(order.getUserId()),
                order.getCreatedAt(),
                null,
                null
        );
    }

    public void recordOrderSubmitted(Order order) {
        Instant now = Instant.now();
        persistEvent(order, OrderEventType.ORDER_SUBMITTED,
                new OrderSubmittedPayload(order.getId(), order.getStatus(), now),
                "SYSTEM",
                now,
                null,
                null
        );
    }

    public void recordOrderCancelRequested(Order order, Instant requestedAt, String reason) {
        persistEvent(order, OrderEventType.ORDER_CANCEL_REQUESTED,
                new OrderCancelRequestedPayload(order.getId(), requestedAt, reason),
                userActor(order.getUserId()),
                requestedAt,
                null,
                null
        );
    }

    private void persistEvent(Order order,
                              OrderEventType type,
                              Object payload,
                              String actor,
                              Instant occurredAt,
                              Long referenceId,
                              String referenceType) {
        try {
            OrderEvent event = OrderEvent.builder()
                    .orderId(order.getId())
                    .userId(order.getUserId())
                    .instrumentId(order.getInstrumentId())
                    .eventType(type)
                    .payload(OpenObjectMapper.toJson(payload))
                    .referenceId(referenceId)
                    .referenceType(referenceType)
                    .actor(actor)
                    .occurredAt(occurredAt != null ? occurredAt : Instant.now())
                    .build();
            orderEventRepository.append(event);
        } catch (Exception ex) {
            log.error("Failed to persist order event {} for order {}", type, order.getId(), ex);
            throw OpenServiceException.of(OrderErrorCode.ORDER_STATE_CONFLICT,
                    "Failed to persist order event %s".formatted(type));
        }
    }

    private String userActor(Long userId) {
        return "USER:" + (userId == null ? "UNKNOWN" : userId);
    }
}
