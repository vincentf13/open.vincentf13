package open.vincentf13.exchange.position.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.common.sdk.enums.PositionStatus;
import open.vincentf13.exchange.position.domain.model.Position;
import open.vincentf13.exchange.position.domain.model.PositionEvent;
import open.vincentf13.exchange.position.infra.PositionErrorCode;
import open.vincentf13.exchange.position.infra.persistence.po.PositionPO;
import open.vincentf13.exchange.position.infra.persistence.repository.PositionEventRepository;
import open.vincentf13.exchange.position.infra.persistence.repository.PositionRepository;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionEventItem;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionEventResponse;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionResponse;
import open.vincentf13.sdk.core.exception.OpenException;
import open.vincentf13.sdk.core.object.mapper.OpenObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@RequiredArgsConstructor
@Validated
public class PositionQueryService {

  private final PositionRepository positionRepository;
  private final PositionEventRepository positionEventRepository;

  public PositionResponse getPosition(@NotNull Long userId, @NotNull Long instrumentId) {
    Position position =
        positionRepository
            .findOne(
                Wrappers.lambdaQuery(PositionPO.class)
                    .eq(PositionPO::getUserId, userId)
                    .eq(PositionPO::getInstrumentId, instrumentId)
                    .eq(PositionPO::getStatus, PositionStatus.ACTIVE))
            .orElseThrow(
                () ->
                    OpenException.of(
                        PositionErrorCode.POSITION_NOT_FOUND,
                        Map.of("userId", userId, "instrumentId", instrumentId)));
    return OpenObjectMapper.convert(position, PositionResponse.class);
  }

  public List<PositionResponse> getPositions(Long userId, Long instrumentId) {
    if (userId == null) {
      return List.of();
    }
    List<Position> positions =
        positionRepository.findBy(
            Wrappers.lambdaQuery(PositionPO.class).eq(PositionPO::getUserId, userId));
    if (positions.isEmpty()) {
      return List.of();
    }
    List<Position> sorted = new ArrayList<>(positions);
    Comparator<Position> comparator =
        Comparator.comparing(
                (Position position) -> {
                  if (instrumentId != null && instrumentId.equals(position.getInstrumentId())) {
                    return 0;
                  }
                  return 1;
                })
            .thenComparing(position -> position.getStatus() == PositionStatus.ACTIVE ? 0 : 1)
            .thenComparing(
                (Position position) ->
                    position.getUpdatedAt() == null ? Instant.EPOCH : position.getUpdatedAt(),
                Comparator.reverseOrder())
            .thenComparing(
                position ->
                    position.getInstrumentId() == null
                        ? Long.MAX_VALUE
                        : position.getInstrumentId());
    sorted.sort(comparator);
    return OpenObjectMapper.convertList(sorted, PositionResponse.class);
  }

  public PositionEventResponse getPositionEvents(@NotNull Long userId, @NotNull Long positionId) {
    if (userId == null) {
      throw OpenException.of(PositionErrorCode.POSITION_NOT_OWNED);
    }
    Position position =
        positionRepository
            .findOne(
                Wrappers.lambdaQuery(PositionPO.class).eq(PositionPO::getPositionId, positionId))
            .orElseThrow(
                () ->
                    OpenException.of(
                        PositionErrorCode.POSITION_NOT_FOUND, Map.of("positionId", positionId)));
    if (!userId.equals(position.getUserId())) {
      throw OpenException.of(
          PositionErrorCode.POSITION_NOT_OWNED, Map.of("positionId", positionId, "userId", userId));
    }
    List<PositionEvent> events = positionEventRepository.findByPositionId(positionId);
    List<PositionEventItem> items =
        events.stream()
            .map(event -> OpenObjectMapper.convert(event, PositionEventItem.class))
            .filter(java.util.Objects::nonNull)
            .toList();
    return new PositionEventResponse(positionId, Instant.now(), items);
  }
}
