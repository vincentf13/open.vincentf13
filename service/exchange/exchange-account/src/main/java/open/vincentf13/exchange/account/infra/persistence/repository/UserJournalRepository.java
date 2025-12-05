package open.vincentf13.exchange.account.infra.persistence.repository;

import com.github.yitter.idgen.DefaultIdGenerator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.domain.model.UserJournal;
import open.vincentf13.exchange.account.infra.persistence.mapper.UserJournalMapper;
import open.vincentf13.exchange.account.infra.persistence.po.UserJournalPO;
import open.vincentf13.sdk.infra.mysql.OpenMybatisBatchExecutor;
import open.vincentf13.sdk.core.object.mapper.OpenObjectMapper;
import org.springframework.stereotype.Repository;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

@Repository
@Validated
@RequiredArgsConstructor
public class UserJournalRepository {
    
    private final UserJournalMapper mapper;
    private final DefaultIdGenerator idGenerator;
    
    public UserJournal insert(@NotNull @Valid UserJournal journal) {
        if (journal.getJournalId() == null) {
            journal.setJournalId(idGenerator.newLong());
        }
        UserJournalPO po = OpenObjectMapper.convert(journal, UserJournalPO.class);
        mapper.insert(po);
        return journal;
    }

    public List<UserJournal> insertBatch(@NotNull List<@Valid UserJournal> journals) {
        if (journals.isEmpty()) {
            return journals;
        }
        List<UserJournalPO> pos = new ArrayList<>(journals.size());
        for (UserJournal journal : journals) {
            if (journal.getJournalId() == null) {
                journal.setJournalId(idGenerator.newLong());
            }
            pos.add(OpenObjectMapper.convert(journal, UserJournalPO.class));
        }
        OpenMybatisBatchExecutor.execute(UserJournalMapper.class, pos, UserJournalMapper::insert);
        return journals;
    }
}
