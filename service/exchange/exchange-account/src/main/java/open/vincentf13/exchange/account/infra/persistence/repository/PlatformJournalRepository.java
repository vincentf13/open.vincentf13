package open.vincentf13.exchange.account.infra.persistence.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.github.yitter.idgen.DefaultIdGenerator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.domain.model.PlatformJournal;
import open.vincentf13.exchange.account.infra.persistence.mapper.PlatformJournalMapper;
import open.vincentf13.exchange.account.infra.persistence.po.PlatformJournalPO;
import open.vincentf13.exchange.account.sdk.rest.api.enums.ReferenceType;
import open.vincentf13.sdk.core.object.mapper.OpenObjectMapper;
import org.springframework.stereotype.Repository;
import org.springframework.validation.annotation.Validated;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@Validated
@RequiredArgsConstructor
public class PlatformJournalRepository {
    
    private final PlatformJournalMapper mapper;
    private final DefaultIdGenerator idGenerator;

    public void insertBatch(@NotNull List<@Valid PlatformJournal> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<PlatformJournalPO> pos = records.stream().map(record -> {
            if (record.getJournalId() == null) {
                record.setJournalId(idGenerator.newLong());
            }
            return OpenObjectMapper.convert(record, PlatformJournalPO.class);
        }).toList();
        Db.saveBatch(pos);
    }

    public List<PlatformJournal> findByReference(@NotNull ReferenceType referenceType,
                                                 @NotNull String referenceId) {
        if (referenceId.isBlank()) {
            return List.of();
        }
        var wrapper = Wrappers.<PlatformJournalPO>lambdaQuery()
                .eq(PlatformJournalPO::getReferenceType, referenceType)
                .eq(PlatformJournalPO::getReferenceId, referenceId)
                .orderByDesc(PlatformJournalPO::getCreatedAt)
                .orderByDesc(PlatformJournalPO::getSeq)
                .orderByDesc(PlatformJournalPO::getJournalId);
        return mapper.selectList(wrapper)
                     .stream()
                     .map(po -> OpenObjectMapper.convert(po, PlatformJournal.class))
                     .toList();
    }

    public List<PlatformJournal> findByAccountId(@NotNull Long accountId) {
        var wrapper = Wrappers.<PlatformJournalPO>lambdaQuery()
                .eq(PlatformJournalPO::getAccountId, accountId)
                .orderByDesc(PlatformJournalPO::getCreatedAt)
                .orderByDesc(PlatformJournalPO::getSeq)
                .orderByDesc(PlatformJournalPO::getJournalId);
        return mapper.selectList(wrapper)
                     .stream()
                     .map(po -> OpenObjectMapper.convert(po, PlatformJournal.class))
                     .toList();
    }

    public List<PlatformJournal> findByAccountIdBefore(@NotNull Long accountId,
                                                       @NotNull Instant snapshotAt) {
        var wrapper = Wrappers.<PlatformJournalPO>lambdaQuery()
                .eq(PlatformJournalPO::getAccountId, accountId)
                .le(PlatformJournalPO::getEventTime, snapshotAt)
                .orderByDesc(PlatformJournalPO::getEventTime)
                .orderByDesc(PlatformJournalPO::getSeq)
                .orderByDesc(PlatformJournalPO::getJournalId);
        return mapper.selectList(wrapper)
                     .stream()
                     .map(po -> OpenObjectMapper.convert(po, PlatformJournal.class))
                     .toList();
    }

    public Optional<PlatformJournal> findLatestBefore(@NotNull Long accountId,
                                                      @NotNull Instant snapshotAt) {
        var wrapper = Wrappers.<PlatformJournalPO>lambdaQuery()
                .eq(PlatformJournalPO::getAccountId, accountId)
                .le(PlatformJournalPO::getEventTime, snapshotAt)
                .orderByDesc(PlatformJournalPO::getEventTime)
                .orderByDesc(PlatformJournalPO::getSeq)
                .orderByDesc(PlatformJournalPO::getJournalId)
                .last("LIMIT 1");
        return mapper.selectList(wrapper)
                     .stream()
                     .findFirst()
                     .map(po -> OpenObjectMapper.convert(po, PlatformJournal.class));
    }

    public Map<Long, PlatformJournal> findLatestBefore(@NotNull List<Long> accountIds,
                                                       @NotNull Instant snapshotAt) {
        if (accountIds.isEmpty()) {
            return Map.of();
        }
        var wrapper = Wrappers.<PlatformJournalPO>lambdaQuery()
                .in(PlatformJournalPO::getAccountId, accountIds)
                .le(PlatformJournalPO::getEventTime, snapshotAt)
                .orderByAsc(PlatformJournalPO::getAccountId)
                .orderByDesc(PlatformJournalPO::getEventTime)
                .orderByDesc(PlatformJournalPO::getSeq)
                .orderByDesc(PlatformJournalPO::getJournalId);
        Map<Long, PlatformJournal> result = new HashMap<>();
        for (PlatformJournalPO po : mapper.selectList(wrapper)) {
            Long accountId = po.getAccountId();
            if (accountId == null || result.containsKey(accountId)) {
                continue;
            }
            result.put(accountId, OpenObjectMapper.convert(po, PlatformJournal.class));
        }
        return result;
    }
}
