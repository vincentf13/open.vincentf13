package open.vincentf13.exchange.order.service;

import open.vincentf13.exchange.order.domain.model.Order;
import open.vincentf13.exchange.order.sdk.rest.api.dto.OrderResponse;
import org.springframework.stereotype.Component;

@Component
public class OrderAssembler {

    public OrderResponse toResponse(Order order) {
        if (order == null) {
            return null;
        }
        return new OrderResponse(
                order.getId(),
                order.getClientOrderId(),
                order.getUserId(),
                order.getInstrumentId(),
                order.getSide(),
                order.getType(),
                order.getStatus(),
                order.getTimeInForce(),
                order.getPrice(),
                order.getStopPrice(),
                order.getQuantity(),
                order.getFilledQuantity(),
                order.getRemainingQuantity(),
                order.getAvgFillPrice(),
                order.getFee(),
                order.getSource(),
                order.getVersion(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
