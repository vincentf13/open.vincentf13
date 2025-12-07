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

@Service
@RequiredArgsConstructor
@Validated
public class PositionQueryService {
    
    private final PositionRepository positionRepository;
    
    public PositionIntentResponse determineIntent(@NotNull @Valid PositionIntentRequest request) {
        var activePosition = positionRepository.findOne(
                Wrappers.lambdaQuery(PositionPO.class)
                        .eq(PositionPO::getUserId, request.userId())
                        .eq(PositionPO::getInstrumentId, request.instrumentId())
                        .eq(PositionPO::getStatus, PositionStatus.ACTIVE));
        if (activePosition.isEmpty()) {
            return PositionIntentResponse.of(PositionIntentType.INCREASE, BigDecimal.ZERO);
        }
        Position position = activePosition.get();
        BigDecimal existing = position.getQuantity();
        PositionIntentType intentType = position.evaluateIntent(request.side(), request.quantity());
        if (intentType == PositionIntentType.INCREASE) {
            return PositionIntentResponse.of(intentType, existing);
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
        return PositionIntentResponse.of(intentType, existing, position.getEntryPrice());
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
}
