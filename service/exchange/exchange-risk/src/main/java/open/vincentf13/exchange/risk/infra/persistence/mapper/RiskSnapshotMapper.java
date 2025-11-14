package open.vincentf13.exchange.risk.infra.persistence.mapper;

import open.vincentf13.exchange.risk.infra.persistence.po.RiskSnapshotPO;
import org.apache.ibatis.annotations.Param;

public interface RiskSnapshotMapper {

    RiskSnapshotPO findByUserAndInstrument(@Param("userId") Long userId,
                                           @Param("instrumentId") Long instrumentId);

    int insert(RiskSnapshotPO snapshot);

    int update(RiskSnapshotPO snapshot);
}
