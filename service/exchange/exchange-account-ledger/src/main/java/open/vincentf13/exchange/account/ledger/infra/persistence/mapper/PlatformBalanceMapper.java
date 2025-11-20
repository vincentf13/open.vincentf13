package open.vincentf13.exchange.account.ledger.infra.persistence.mapper;

import open.vincentf13.exchange.account.ledger.infra.persistence.po.PlatformBalancePO;

import java.util.List;

public interface PlatformBalanceMapper {

    List<PlatformBalancePO> findBy(PlatformBalancePO condition);

    int insertSelective(PlatformBalancePO record);

    int updateSelective(PlatformBalancePO record);
}
