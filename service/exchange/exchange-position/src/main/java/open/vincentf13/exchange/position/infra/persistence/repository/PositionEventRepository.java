package open.vincentf13.exchange.position.infra.persistence.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.yitter.idgen.DefaultIdGenerator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.position.domain.model.PositionEvent;
import open.vincentf13.exchange.position.infra.PositionErrorCode;
import open.vincentf13.exchange.position.infra.persistence.mapper.PositionEventMapper;
import open.vincentf13.exchange.position.infra.persistence.po.PositionEventPO;
import open.vincentf13.exchange.position.sdk.rest.api.enums.PositionReferenceType;
import open.vincentf13.sdk.core.exception.OpenException;
import open.vincentf13.sdk.core.mapper.OpenObjectMapper;
import open.vincentf13.sdk.infra.mysql.OpenMybatisBatchExecutor;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;
import org.springframework.validation.annotation.Validated;

@Repository
@RequiredArgsConstructor
@Validated
public class PositionEventRepository {

  private final PositionEventMapper mapper;
  private final DefaultIdGenerator idGenerator;
  private final SqlSessionFactory sqlSessionFactory;

  public void insert(@NotNull @Valid PositionEvent event) {
    if (event.getEventId() == null) {
      event.setEventId(idGenerator.newLong());
    }
    if (event.getSequenceNumber() == null) {
      Long current = mapper.selectMaxSequenceForUpdate(event.getPositionId());
      long nextSequence = current == null ? 1L : current + 1L;
      event.setSequenceNumber(nextSequence);
    }
    PositionEventPO po = OpenObjectMapper.convert(event, PositionEventPO.class);
    mapper.insert(po);
  }

  public void insertBatch(@NotNull List<@Valid PositionEvent> events) {
    if (events.isEmpty()) {
      return;
    }
    OpenMybatisBatchExecutor batchExecutor = new OpenMybatisBatchExecutor(sqlSessionFactory);
    batchExecutor.execute(
        events,
        (session, event) -> {
          PositionEventMapper batchMapper = session.getMapper(PositionEventMapper.class);
          if (event.getEventId() == null) {
            event.setEventId(idGenerator.newLong());
          }
          if (event.getSequenceNumber() == null) {
            Long current = batchMapper.selectMaxSequenceForUpdate(event.getPositionId());
            long nextSequence = current == null ? 1L : current + 1L;
            event.setSequenceNumber(nextSequence);
          }
          PositionEventPO po = OpenObjectMapper.convert(event, PositionEventPO.class);
          batchMapper.insert(po);
        });
  }

  public boolean existsByReference(
      @NotNull PositionReferenceType referenceType, @NotNull String referenceId) {
    try {
      return mapper.selectCount(
              com.baomidou.mybatisplus.core.toolkit.Wrappers.lambdaQuery(PositionEventPO.class)
                  .eq(PositionEventPO::getReferenceType, referenceType)
                  .eq(PositionEventPO::getReferenceId, referenceId))
          > 0;
    } catch (DataAccessException e) {
      throw OpenException.of(
          PositionErrorCode.POSITION_CONCURRENT_UPDATE, Map.of("referenceId", referenceId), e);
    }
  }

  public boolean existsByReferenceLike(
      @NotNull PositionReferenceType referenceType, @NotNull String referenceId) {
    try {
      return mapper.selectCount(
              com.baomidou.mybatisplus.core.toolkit.Wrappers.lambdaQuery(PositionEventPO.class)
                  .eq(PositionEventPO::getReferenceType, referenceType)
                  .likeRight(PositionEventPO::getReferenceId, referenceId))
          > 0;
    } catch (DataAccessException e) {
      throw OpenException.of(
          PositionErrorCode.POSITION_CONCURRENT_UPDATE, Map.of("referenceId", referenceId), e);
    }
  }

  public PositionEvent findLatestByReferencePrefix(
      @NotNull PositionReferenceType referenceType, @NotNull String referencePrefix) {
    try {
      PositionEventPO po =
          mapper.selectOne(
              Wrappers.lambdaQuery(PositionEventPO.class)
                  .eq(PositionEventPO::getReferenceType, referenceType)
                  .likeRight(PositionEventPO::getReferenceId, referencePrefix)
                  .orderByDesc(PositionEventPO::getEventId)
                  .last("LIMIT 1"));
      return OpenObjectMapper.convert(po, PositionEvent.class);
    } catch (DataAccessException e) {
      throw OpenException.of(
          PositionErrorCode.POSITION_CONCURRENT_UPDATE, Map.of("referenceId", referencePrefix), e);
    }
  }

  public boolean existsByReferenceAndUser(
      @NotNull PositionReferenceType referenceType,
      @NotNull String referenceId,
      @NotNull Long userId) {
    return mapper.selectCount(
            Wrappers.lambdaQuery(PositionEventPO.class)
                .eq(PositionEventPO::getReferenceType, referenceType)
                .eq(PositionEventPO::getReferenceId, referenceId)
                .eq(PositionEventPO::getUserId, userId))
        > 0;
  }

  public List<PositionEvent> findByPositionId(@NotNull Long positionId) {
    return mapper
        .selectList(
            Wrappers.lambdaQuery(PositionEventPO.class)
                .eq(PositionEventPO::getPositionId, positionId)
                .orderByDesc(PositionEventPO::getSequenceNumber))
        .stream()
        .map(po -> OpenObjectMapper.convert(po, PositionEvent.class))
        .filter(Objects::nonNull)
        .toList();
  }
}
