package open.vincentf13.exchange.market.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import open.vincentf13.exchange.market.infra.persistence.po.KlineBucketPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface KlineBucketMapper extends BaseMapper<KlineBucketPO> {
}
