package open.vincentf13.exchange.position.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.common.sdk.enums.PositionIntentType;
import open.vincentf13.exchange.common.sdk.enums.PositionStatus;
import open.vincentf13.exchange.position.domain.model.Position;
import open.vincentf13.exchange.position.infra.PositionErrorCode;
import open.vincentf13.exchange.position.infra.persistence.po.PositionPO;
import open.vincentf13.exchange.position.infra.persistence.repository.PositionRepository;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionIntentRequest;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionIntentResponse;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionResponse;
import open.vincentf13.sdk.core.exception.OpenException;
import open.vincentf13.sdk.core.object.mapper.OpenObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Validated
public class PositionQueryService {
    
    private final PositionRepository positionRepository;
    
    public PositionIntentResponse prepareIntent(@NotNull @Valid PositionIntentRequest request) {
        var activePosition = positionRepository.findOne(
                Wrappers.lambdaQuery(PositionPO.class)
                        .eq(PositionPO::getUserId, request.userId())
                        .eq(PositionPO::getInstrumentId, request.instrumentId())
                        .eq(PositionPO::getStatus, PositionStatus.ACTIVE));
        if (activePosition.isEmpty()) {
            return PositionIntentResponse.of(PositionIntentType.INCREASE, BigDecimal.ZERO, null);
        }
        Position position = activePosition.get();
        BigDecimal existing = position.getQuantity();
        PositionIntentType intentType = position.evaluateIntent(request.side(), request.quantity());
        if (intentType == PositionIntentType.INCREASE) {
            return PositionIntentResponse.of(intentType, existing, OpenObjectMapper.convert(position, PositionResponse.class));
        }
        BigDecimal availableToClose = position.availableToClose();
        if (availableToClose.compareTo(request.quantity()) < 0) {
            return PositionIntentResponse.ofRejected(intentType, existing,
                                                     PositionErrorCode.POSITION_INSUFFICIENT_AVAILABLE.code());
        }
        int expectedVersion = position.safeVersion();
        Position updateRecord = Position.builder()
                                        .positionId(position.getPositionId())
                                        .closingReservedQuantity(position.getClosingReservedQuantity().add(request.quantity()))
                                        .version(expectedVersion + 1)
                                        .build();
        boolean updated = positionRepository.updateSelectiveBy(
                updateRecord,
                new LambdaUpdateWrapper<PositionPO>()
                        .eq(PositionPO::getPositionId, position.getPositionId())
                        .eq(PositionPO::getUserId, position.getUserId())
                        .eq(PositionPO::getInstrumentId, position.getInstrumentId())
                        .eq(PositionPO::getStatus, PositionStatus.ACTIVE)
                        .eq(PositionPO::getVersion, expectedVersion));
        if (!updated) {
            return PositionIntentResponse.ofRejected(intentType, existing,
                                                     PositionErrorCode.POSITION_CONCURRENT_UPDATE.code());
        }
        return PositionIntentResponse.of(intentType,
                                         existing,
                                         OpenObjectMapper.convert(position, PositionResponse.class));
    }
    
    public PositionResponse getPosition(@NotNull Long userId,
                                        @NotNull Long instrumentId) {
        Position position = positionRepository.findOne(
                                                      Wrappers.lambdaQuery(PositionPO.class)
                                                              .eq(PositionPO::getUserId, userId)
                                                              .eq(PositionPO::getInstrumentId, instrumentId)
                                                              .eq(PositionPO::getStatus, PositionStatus.ACTIVE))
                                              .orElseThrow(() -> OpenException.of(PositionErrorCode.POSITION_NOT_FOUND,
                                                                                  Map.of("userId", userId, "instrumentId", instrumentId)));
        return OpenObjectMapper.convert(position, PositionResponse.class);
    }

    public List<PositionResponse> getPositions(Long userId,
                                               Long instrumentId) {
        if (userId == null) {
            return List.of();
        }
        List<Position> positions = positionRepository.findBy(
                Wrappers.lambdaQuery(PositionPO.class)
                        .eq(PositionPO::getUserId, userId));
        if (positions.isEmpty()) {
            return List.of();
        }
        List<Position> sorted = new ArrayList<>(positions);
        Comparator<Position> comparator = Comparator
                .comparing((Position position) -> {
                    if (instrumentId != null && instrumentId.equals(position.getInstrumentId())) {
                        return 0;
                    }
                    return 1;
                })
                .thenComparing(position -> position.getStatus() == PositionStatus.ACTIVE ? 0 : 1)
                .thenComparing(position -> position.getInstrumentId() == null ? Long.MAX_VALUE : position.getInstrumentId());
        sorted.sort(comparator);
        return OpenObjectMapper.convertList(sorted, PositionResponse.class);
    }
}
