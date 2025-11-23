package open.vincentf13.exchange.admin.infra.persistence.mapper;

import open.vincentf13.exchange.admin.infra.persistence.po.InstrumentPO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface InstrumentMapper {

    List<InstrumentPO> findBy(InstrumentPO condition);

    InstrumentPO findOne(InstrumentPO condition);
}
