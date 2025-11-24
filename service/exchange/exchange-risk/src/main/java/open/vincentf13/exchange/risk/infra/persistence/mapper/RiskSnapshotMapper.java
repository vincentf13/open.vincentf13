package open.vincentf13.exchange.risk.infra.persistence.mapper;

import open.vincentf13.exchange.risk.infra.persistence.po.RiskSnapshotPO;

public interface RiskSnapshotMapper {

    java.util.List<RiskSnapshotPO> findBy(RiskSnapshotPO condition);

    int insertSelective(RiskSnapshotPO snapshot);

    int updateStatusByIdAndVersion(@Param("snapshotId") Long snapshotId,
                                   @Param("status") String status,
                                   @Param("expectedVersion") Integer expectedVersion);
}
