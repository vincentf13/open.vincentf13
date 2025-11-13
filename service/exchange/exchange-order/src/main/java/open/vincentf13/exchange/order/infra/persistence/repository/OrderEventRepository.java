package open.vincentf13.exchange.order.infra.persistence.repository;

import open.vincentf13.exchange.order.domain.model.OrderEvent;

import java.util.List;

public interface OrderEventRepository {

    void append(OrderEvent event);

    List<OrderEvent> findByOrderId(Long orderId);

    long nextSequence(Long orderId);

    boolean existsByReference(Long orderId, String referenceType, Long referenceId);
}
