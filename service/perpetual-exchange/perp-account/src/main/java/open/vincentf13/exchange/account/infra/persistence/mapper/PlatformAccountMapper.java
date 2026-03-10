package open.vincentf13.exchange.account.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import open.vincentf13.exchange.account.infra.persistence.po.PlatformAccountPO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface PlatformAccountMapper extends BaseMapper<PlatformAccountPO> {
    
    int batchUpdateWithOptimisticLock(@Param("list") List<?> list);
}
