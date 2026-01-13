package open.vincentf13.exchange.admin.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import open.vincentf13.exchange.admin.infra.persistence.po.InstrumentPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InstrumentMapper extends BaseMapper<InstrumentPO> {}
