package open.vincentf13.exchange.order.infra.persistence.mapper;

import open.vincentf13.exchange.order.infra.persistence.po.OrderPO;
import open.vincentf13.exchange.order.sdk.rest.api.dto.OrderStatus;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;

public interface OrderMapper {

    void insert(OrderPO order);

    OrderPO findById(@Param("orderId") Long orderId);

    OrderPO findByUserAndClientOrderId(@Param("userId") Long userId,
                                       @Param("clientOrderId") String clientOrderId);

    int updateStatus(@Param("orderId") Long orderId,
                     @Param("userId") Long userId,
                     @Param("status") OrderStatus status,
                     @Param("updatedAt") Instant updatedAt,
                     @Param("expectedVersion") Integer expectedVersion);
}
