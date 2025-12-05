package open.vincentf13.exchange.account.infra.persistence.repository;

import com.github.yitter.idgen.DefaultIdGenerator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.domain.model.UserJournal;
import open.vincentf13.exchange.account.infra.persistence.mapper.UserJournalMapper;
import open.vincentf13.exchange.account.infra.persistence.po.UserJournalPO;
import open.vincentf13.sdk.core.object.mapper.OpenObjectMapper;
import org.springframework.stereotype.Repository;
import org.springframework.validation.annotation.Validated;

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
}
