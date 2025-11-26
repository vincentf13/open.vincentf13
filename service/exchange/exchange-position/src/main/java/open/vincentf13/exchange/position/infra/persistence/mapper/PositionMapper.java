package open.vincentf13.exchange.position.infra.persistence.mapper;

import open.vincentf13.exchange.position.infra.persistence.po.PositionPO;
import open.vincentf13.exchange.common.sdk.enums.PositionSide;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface PositionMapper {

    List<PositionPO> findBy(PositionPO condition);

    int insertSelective(PositionPO po);

    int updateSelectiveBy(@Param("record") PositionPO record,
                          @Param("positionId") Long positionId,
                          @Param("userId") Long userId,
                          @Param("instrumentId") Long instrumentId,
                          @Param("side") PositionSide side,
                          @Param("expectedVersion") Integer expectedVersion,
                          @Param("status") String status);
}
