package open.vincentf13.exchange.account.infra.persistence.repository;

import com.github.yitter.idgen.DefaultIdGenerator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.domain.model.PlatformJournal;
import open.vincentf13.exchange.account.infra.persistence.mapper.PlatformJournalMapper;
import open.vincentf13.exchange.account.infra.persistence.po.PlatformJournalPO;
import open.vincentf13.sdk.core.object.mapper.OpenObjectMapper;
import org.springframework.stereotype.Repository;
import org.springframework.validation.annotation.Validated;

@Repository
@Validated
@RequiredArgsConstructor
public class PlatformJournalRepository {
    
    private final PlatformJournalMapper mapper;
    private final DefaultIdGenerator idGenerator;
    
    public PlatformJournal insert(@NotNull @Valid PlatformJournal journal) {
        if (journal.getJournalId() == null) {
            journal.setJournalId(idGenerator.newLong());
        }
        PlatformJournalPO po = OpenObjectMapper.convert(journal, PlatformJournalPO.class);
        mapper.insert(po);
        return journal;
    }
}
