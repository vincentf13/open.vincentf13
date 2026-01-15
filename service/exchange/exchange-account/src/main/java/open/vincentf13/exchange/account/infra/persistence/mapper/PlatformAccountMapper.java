package open.vincentf13.exchange.account.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import open.vincentf13.exchange.account.infra.persistence.po.PlatformAccountPO;
import org.apache.ibatis.annotations.Param;

public interface PlatformAccountMapper extends BaseMapper<PlatformAccountPO> {

  int batchUpdateWithOptimisticLock(@Param("list") List<?> list);
}
