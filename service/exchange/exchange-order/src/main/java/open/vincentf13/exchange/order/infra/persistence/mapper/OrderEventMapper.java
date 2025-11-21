package open.vincentf13.exchange.order.infra.persistence.mapper;

import open.vincentf13.exchange.order.infra.persistence.po.OrderEventPO;

import java.util.List;

public interface OrderEventMapper {

    void insert(OrderEventPO event);

    List<OrderEventPO> findBy(OrderEventPO condition);
}
