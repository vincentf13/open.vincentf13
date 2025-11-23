package open.vincentf13.exchange.position.infra.persistence.mapper;

import open.vincentf13.exchange.position.infra.persistence.po.PositionPO;
import open.vincentf13.exchange.position.sdk.rest.api.enums.PositionSide;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;

public interface PositionMapper {

    PositionPO findBy(PositionPO condition);

    int insertDefault(PositionPO po);

    int reserveForClose(@Param("userId") Long userId,
                        @Param("instrumentId") Long instrumentId,
                        @Param("quantity") BigDecimal quantity,
                        @Param("side") PositionSide side);

    int updateLeverage(@Param("positionId") Long positionId,
                       @Param("leverage") Integer leverage);
}
