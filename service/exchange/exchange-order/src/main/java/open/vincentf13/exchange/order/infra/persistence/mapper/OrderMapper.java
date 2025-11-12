package open.vincentf13.exchange.order.infra.persistence.mapper;

import open.vincentf13.exchange.order.infra.persistence.po.OrderPO;
public interface OrderMapper {

    void insertSelective(OrderPO order);

    OrderPO findBy(OrderPO condition);

    int updateSelective(OrderPO order);
}
