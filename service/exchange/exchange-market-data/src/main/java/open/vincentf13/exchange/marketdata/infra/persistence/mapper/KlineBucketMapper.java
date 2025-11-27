package open.vincentf13.exchange.marketdata.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import open.vincentf13.exchange.marketdata.infra.persistence.po.KlineBucketPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface KlineBucketMapper extends BaseMapper<KlineBucketPO> {
}
