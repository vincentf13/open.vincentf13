package open.vincentf13.exchange.account.ledger.infra.persistence.mapper;

import open.vincentf13.exchange.account.ledger.infra.persistence.po.LedgerEntryPO;
import java.util.List;

public interface LedgerEntryMapper {

    int insert(LedgerEntryPO record);

    List<LedgerEntryPO> findBy(LedgerEntryPO condition);
}
