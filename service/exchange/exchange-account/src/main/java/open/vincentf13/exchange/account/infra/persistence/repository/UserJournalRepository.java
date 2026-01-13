package open.vincentf13.exchange.account.infra.persistence.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.github.yitter.idgen.DefaultIdGenerator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.domain.model.UserJournal;
import open.vincentf13.exchange.account.infra.persistence.mapper.UserJournalMapper;
import open.vincentf13.exchange.account.infra.persistence.po.UserJournalPO;
import open.vincentf13.exchange.account.sdk.rest.api.enums.ReferenceType;
import open.vincentf13.exchange.common.sdk.enums.AssetSymbol;
import open.vincentf13.sdk.core.mapper.OpenObjectMapper;
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
    Db.saveBatch(pos);
    return journals;
  }

  public Optional<UserJournal> findLatestByReference(
      @NotNull Long accountId,
      @NotNull AssetSymbol asset,
      @NotNull ReferenceType referenceType,
      @NotNull String referenceId) {
    var wrapper =
        Wrappers.<UserJournalPO>lambdaQuery()
            .eq(UserJournalPO::getAccountId, accountId)
            .eq(UserJournalPO::getAsset, asset)
            .eq(UserJournalPO::getReferenceType, referenceType)
            .eq(UserJournalPO::getReferenceId, referenceId)
            .orderByDesc(UserJournalPO::getCreatedAt)
            .orderByDesc(UserJournalPO::getSeq)
            .orderByDesc(UserJournalPO::getJournalId)
            .last("LIMIT 1");
    return mapper.selectList(wrapper).stream()
        .findFirst()
        .map(po -> OpenObjectMapper.convert(po, UserJournal.class));
  }

  public List<UserJournal> findByAccountId(@NotNull Long userId, @NotNull Long accountId) {
    var wrapper =
        Wrappers.<UserJournalPO>lambdaQuery()
            .eq(UserJournalPO::getUserId, userId)
            .eq(UserJournalPO::getAccountId, accountId)
            .orderByDesc(UserJournalPO::getCreatedAt)
            .orderByDesc(UserJournalPO::getSeq)
            .orderByDesc(UserJournalPO::getJournalId);
    return mapper.selectList(wrapper).stream()
        .map(po -> OpenObjectMapper.convert(po, UserJournal.class))
        .toList();
  }

  public List<UserJournal> findByReference(
      @NotNull ReferenceType referenceType, @NotNull String referenceId) {
    if (referenceId.isBlank()) {
      return List.of();
    }
    var wrapper =
        Wrappers.<UserJournalPO>lambdaQuery()
            .eq(UserJournalPO::getReferenceType, referenceType)
            .eq(UserJournalPO::getReferenceId, referenceId)
            .orderByDesc(UserJournalPO::getCreatedAt)
            .orderByDesc(UserJournalPO::getSeq)
            .orderByDesc(UserJournalPO::getJournalId);
    return mapper.selectList(wrapper).stream()
        .map(po -> OpenObjectMapper.convert(po, UserJournal.class))
        .toList();
  }

  public List<UserJournal> findByAccountIdBefore(
      @NotNull Long userId, @NotNull Long accountId, @NotNull Instant snapshotAt) {
    var wrapper =
        Wrappers.<UserJournalPO>lambdaQuery()
            .eq(UserJournalPO::getUserId, userId)
            .eq(UserJournalPO::getAccountId, accountId)
            .le(UserJournalPO::getEventTime, snapshotAt)
            .orderByDesc(UserJournalPO::getEventTime)
            .orderByDesc(UserJournalPO::getSeq)
            .orderByDesc(UserJournalPO::getJournalId);
    return mapper.selectList(wrapper).stream()
        .map(po -> OpenObjectMapper.convert(po, UserJournal.class))
        .toList();
  }

  public Optional<UserJournal> findLatestBefore(
      @NotNull Long accountId, @NotNull Instant snapshotAt) {
    var wrapper =
        Wrappers.<UserJournalPO>lambdaQuery()
            .eq(UserJournalPO::getAccountId, accountId)
            .le(UserJournalPO::getEventTime, snapshotAt)
            .orderByDesc(UserJournalPO::getEventTime)
            .orderByDesc(UserJournalPO::getSeq)
            .orderByDesc(UserJournalPO::getJournalId)
            .last("LIMIT 1");
    return mapper.selectList(wrapper).stream()
        .findFirst()
        .map(po -> OpenObjectMapper.convert(po, UserJournal.class));
  }

  public Map<Long, UserJournal> findLatestBefore(
      @NotNull List<Long> accountIds, @NotNull Instant snapshotAt) {
    if (accountIds.isEmpty()) {
      return Map.of();
    }
    var wrapper =
        Wrappers.<UserJournalPO>lambdaQuery()
            .in(UserJournalPO::getAccountId, accountIds)
            .le(UserJournalPO::getEventTime, snapshotAt)
            .orderByAsc(UserJournalPO::getAccountId)
            .orderByDesc(UserJournalPO::getEventTime)
            .orderByDesc(UserJournalPO::getSeq)
            .orderByDesc(UserJournalPO::getJournalId);
    Map<Long, UserJournal> result = new HashMap<>();
    for (UserJournalPO po : mapper.selectList(wrapper)) {
      Long accountId = po.getAccountId();
      if (accountId == null || result.containsKey(accountId)) {
        continue;
      }
      result.put(accountId, OpenObjectMapper.convert(po, UserJournal.class));
    }
    return result;
  }
}
