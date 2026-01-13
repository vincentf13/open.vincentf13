package open.vincentf13.exchange.order.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import open.vincentf13.exchange.order.infra.persistence.po.OrderEventPO;
import open.vincentf13.exchange.order.sdk.rest.api.enums.OrderEventReferenceType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface OrderEventMapper extends BaseMapper<OrderEventPO> {

  @Select(
      "SELECT COALESCE(MAX(sequence_number), 0) FROM order_events WHERE order_id = #{orderId} FOR UPDATE")
  Long selectMaxSequenceForUpdate(@Param("orderId") Long orderId);

  @Select(
      "SELECT COUNT(1) FROM order_events WHERE order_id = #{orderId} AND reference_type = #{referenceType} AND reference_id = #{referenceId}")
  long countByReference(
      @Param("orderId") Long orderId,
      @Param("referenceType") OrderEventReferenceType referenceType,
      @Param("referenceId") String referenceId);
}
