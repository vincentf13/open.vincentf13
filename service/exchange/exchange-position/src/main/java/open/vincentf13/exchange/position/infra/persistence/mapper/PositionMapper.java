package open.vincentf13.exchange.position.infra.persistence.mapper;

import open.vincentf13.exchange.order.sdk.rest.api.dto.OrderSide;
import open.vincentf13.exchange.position.infra.persistence.po.PositionPO;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

public interface PositionMapper {

    PositionPO findActive(@Param("userId") Long userId, @Param("instrumentId") Long instrumentId);

    List<PositionPO> findByUser(@Param("userId") Long userId);

    int reserveForClose(@Param("userId") Long userId,
                        @Param("instrumentId") Long instrumentId,
                        @Param("quantity") BigDecimal quantity,
                        @Param("side") OrderSide side);
}
