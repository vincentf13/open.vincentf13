package open.vincentf13.exchange.risk.infra.persistence.mapper;

import open.vincentf13.exchange.risk.infra.persistence.po.RiskLimitPO;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

public interface RiskLimitMapper {

    RiskLimitPO findEffective(@Param("instrumentId") Long instrumentId,
                              @Param("tier") Integer tier,
                              @Param("asOf") Instant asOf);

    List<RiskLimitPO> findActiveByInstrument(@Param("instrumentId") Long instrumentId,
                                             @Param("asOf") Instant asOf);

    int insert(RiskLimitPO record);

    int update(RiskLimitPO record);
}
