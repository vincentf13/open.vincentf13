package open.vincentf13.exchange.account.ledger.infra.persistence.mapper;

import open.vincentf13.exchange.account.ledger.infra.persistence.po.LedgerBalancePO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface LedgerBalanceMapper {

    List<LedgerBalancePO> findBy(LedgerBalancePO condition);

    int insertSelective(LedgerBalancePO record);

    int updateSelective(@Param("record") LedgerBalancePO record,
                        @Param("id") Long id,
                        @Param("expectedVersion") Integer expectedVersion);
}
