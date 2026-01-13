package open.vincentf13.exchange.position.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import open.vincentf13.exchange.position.infra.persistence.po.PositionEventPO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface PositionEventMapper extends BaseMapper<PositionEventPO> {

  @Select(
      "SELECT COALESCE(MAX(sequence_number), 0) FROM position_events WHERE position_id = #{positionId} FOR UPDATE")
  Long selectMaxSequenceForUpdate(@Param("positionId") Long positionId);
}
