package open.vincentf13.exchange.order.infra.persistence.mapper;

import open.vincentf13.exchange.order.infra.persistence.po.OrderPO;
import open.vincentf13.exchange.order.sdk.rest.api.enums.OrderStatus;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface OrderMapper {

    int insertSelective(OrderPO order);

    List<OrderPO> findBy(OrderPO condition);

    int updateStatusByIdAndVersion(@Param("orderId") Long orderId,
                                   @Param("userId") Long userId,
                                   @Param("targetStatus") OrderStatus targetStatus,
                                   @Param("submittedAt") Instant submittedAt,
                                   @Param("filledAt") Instant filledAt,
                                   @Param("expectedVersion") Integer expectedVersion);

    int updateStatusByCurrentStatus(@Param("orderId") Long orderId,
                                    @Param("userId") Long userId,
                                    @Param("currentStatus") OrderStatus currentStatus,
                                    @Param("targetStatus") OrderStatus targetStatus,
                                    @Param("submittedAt") Instant submittedAt,
                                    @Param("filledAt") Instant filledAt);

    int updateStatusWithCost(@Param("orderId") Long orderId,
                             @Param("userId") Long userId,
                             @Param("currentStatus") OrderStatus currentStatus,
                             @Param("targetStatus") OrderStatus targetStatus,
                             @Param("submittedAt") Instant submittedAt,
                             @Param("filledAt") Instant filledAt,
                             @Param("closeCostPrice") BigDecimal closeCostPrice);
}
