package open.vincentf13.exchange.order.infra.persistence.mapper;

import open.vincentf13.exchange.order.infra.persistence.po.OrderEventPO;

public interface OrderEventMapper {

    void insertWithSequence(OrderEventPO event);
}
