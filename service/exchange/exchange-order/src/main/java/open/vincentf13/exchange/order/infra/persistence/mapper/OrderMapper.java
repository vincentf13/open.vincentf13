package open.vincentf13.exchange.order.infra.persistence.mapper;

import open.vincentf13.exchange.order.infra.persistence.po.OrderPO;
import open.vincentf13.exchange.order.sdk.rest.api.enums.OrderStatus;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface OrderMapper {

    int insertSelective(OrderPO order);

    List<OrderPO> findBy(OrderPO condition);

    int updateSelectiveBy(@Param("record") OrderPO record,
                          @Param("orderId") Long orderId,
                          @Param("userId") Long userId,
                          @Param("expectedVersion") Integer expectedVersion);

    int updateSelectiveByCurrentStatus(@Param("record") OrderPO record,
                                       @Param("orderId") Long orderId,
                                       @Param("userId") Long userId,
                                       @Param("currentStatus") OrderStatus currentStatus);
}
