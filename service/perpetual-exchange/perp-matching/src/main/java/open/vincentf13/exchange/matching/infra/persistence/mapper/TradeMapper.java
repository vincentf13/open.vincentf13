package open.vincentf13.exchange.matching.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import open.vincentf13.exchange.matching.infra.persistence.po.TradePO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TradeMapper extends BaseMapper<TradePO> {}
