package open.vincentf13.exchange.risk.infra.persistence.mapper;

import open.vincentf13.exchange.risk.infra.persistence.po.RiskLimitPO;
import org.apache.ibatis.annotations.Param;

public interface RiskLimitMapper {

    RiskLimitPO findBy(RiskLimitPO condition);

    RiskLimitPO findEffective(@Param("instrumentId") Long instrumentId);

    int insertSelective(RiskLimitPO record);

    int updateSelective(RiskLimitPO record);
}
