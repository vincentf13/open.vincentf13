package open.vincentf13.exchange.order.infra.persistence.mapper;

import open.vincentf13.exchange.order.infra.persistence.po.OrderPO;
import open.vincentf13.exchange.order.sdk.rest.api.dto.OrderStatus;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;

public interface OrderMapper {

    void insertSelective(OrderPO order);

    OrderPO findBy(OrderPO condition);

    int updateSelective(OrderPO order);

    int updateStatusByCurrentStatus(@Param("orderId") Long orderId,
                                    @Param("userId") Long userId,
                                    @Param("currentStatus") OrderStatus currentStatus,
                                    @Param("targetStatus") OrderStatus targetStatus,
                                    @Param("updatedAt") Instant updatedAt,
                                    @Param("submittedAt") Instant submittedAt,
                                    @Param("filledAt") Instant filledAt);
}
