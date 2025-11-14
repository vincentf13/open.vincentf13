package open.vincentf13.exchange.risk.infra.persistence.mapper;

import open.vincentf13.exchange.risk.infra.persistence.po.RiskSnapshotPO;

public interface RiskSnapshotMapper {

    RiskSnapshotPO findBy(RiskSnapshotPO condition);

    int insertSelective(RiskSnapshotPO snapshot);

    int updateSelective(RiskSnapshotPO snapshot);
}
