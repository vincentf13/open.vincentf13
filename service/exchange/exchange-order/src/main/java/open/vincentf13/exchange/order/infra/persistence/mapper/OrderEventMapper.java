package open.vincentf13.exchange.order.infra.persistence.mapper;

import open.vincentf13.exchange.order.infra.persistence.po.OrderEventPO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface OrderEventMapper {

    void insert(OrderEventPO event);

    List<OrderEventPO> findByOrderId(@Param("orderId") Long orderId);

    Long nextSequence(@Param("orderId") Long orderId);

    Integer countByReference(@Param("orderId") Long orderId,
                             @Param("referenceType") String referenceType,
                             @Param("referenceId") Long referenceId);
}
