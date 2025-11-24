package open.vincentf13.exchange.account.ledger.infra.persistence.mapper;

import open.vincentf13.exchange.account.ledger.infra.persistence.po.PlatformBalancePO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface PlatformBalanceMapper {

    List<PlatformBalancePO> findBy(PlatformBalancePO condition);

    int insertSelective(PlatformBalancePO record);

    int updateSelective(@Param("record") PlatformBalancePO record,
                        @Param("id") Long id,
                        @Param("expectedVersion") Integer expectedVersion);
}
