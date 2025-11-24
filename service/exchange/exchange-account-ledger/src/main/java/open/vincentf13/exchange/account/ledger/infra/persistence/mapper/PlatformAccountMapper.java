package open.vincentf13.exchange.account.ledger.infra.persistence.mapper;

import open.vincentf13.exchange.account.ledger.infra.persistence.po.PlatformAccountPO;

import java.util.List;

public interface PlatformAccountMapper {

    List<PlatformAccountPO> findBy(PlatformAccountPO condition);

    int insertSelective(PlatformAccountPO record);
}
