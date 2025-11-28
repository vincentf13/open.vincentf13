package open.vincentf13.exchange.position.service;

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
import java.util.Map;

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
        BigDecimal existing = activePosition
                                      .map(position -> position.getQuantity())
                                      .orElse(BigDecimal.ZERO);
        PositionIntentType intentType = activePosition
                                                .map(position -> position.evaluateIntent(request.side(), request.quantity()))
                                                .orElse(PositionIntentType.INCREASE);
        return PositionIntentResponse.of(intentType, existing);
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
