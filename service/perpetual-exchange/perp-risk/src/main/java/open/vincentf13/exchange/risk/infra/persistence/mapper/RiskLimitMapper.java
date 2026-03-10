package open.vincentf13.exchange.risk.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import open.vincentf13.exchange.risk.infra.persistence.po.RiskLimitPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RiskLimitMapper extends BaseMapper<RiskLimitPO> {
}
