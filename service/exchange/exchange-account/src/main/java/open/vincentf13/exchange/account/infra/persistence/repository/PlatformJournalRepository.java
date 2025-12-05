package open.vincentf13.exchange.account.infra.persistence.repository;

import com.github.yitter.idgen.DefaultIdGenerator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.domain.model.PlatformJournal;
import open.vincentf13.exchange.account.infra.persistence.mapper.PlatformJournalMapper;
import open.vincentf13.exchange.account.infra.persistence.po.PlatformJournalPO;
import open.vincentf13.sdk.infra.mysql.OpenMybatisBatchExecutor;
import open.vincentf13.sdk.core.object.mapper.OpenObjectMapper;
import org.springframework.stereotype.Repository;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

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

    public List<PlatformJournal> insertBatch(@NotNull List<@Valid PlatformJournal> journals) {
        if (journals.isEmpty()) {
            return journals;
        }
        List<PlatformJournalPO> pos = new ArrayList<>(journals.size());
        for (PlatformJournal journal : journals) {
            if (journal.getJournalId() == null) {
                journal.setJournalId(idGenerator.newLong());
            }
            pos.add(OpenObjectMapper.convert(journal, PlatformJournalPO.class));
        }
        OpenMybatisBatchExecutor.execute(PlatformJournalMapper.class, pos, PlatformJournalMapper::insert);
        return journals;
    }
}
