package open.vincentf13.exchange.order.infra.persistence.repository;

import open.vincentf13.exchange.order.domain.model.OrderEvent;

public interface OrderEventRepository {

    void append(OrderEvent event);
}
