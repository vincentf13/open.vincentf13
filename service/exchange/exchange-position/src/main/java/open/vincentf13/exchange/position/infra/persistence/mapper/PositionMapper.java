package open.vincentf13.exchange.position.infra.persistence.mapper;

import open.vincentf13.exchange.position.infra.persistence.po.PositionPO;
import open.vincentf13.exchange.position.sdk.rest.api.enums.PositionSide;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

public interface PositionMapper {

    List<PositionPO> findBy(PositionPO condition);

    int insertSelective(PositionPO po);

    int reserveForClose(@Param("userId") Long userId,
                        @Param("instrumentId") Long instrumentId,
                        @Param("quantity") BigDecimal quantity,
                        @Param("side") PositionSide side,
                        @Param("expectedVersion") int expectedVersion);

    int updateLeverage(@Param("positionId") Long positionId,
                       @Param("leverage") Integer leverage);
}
