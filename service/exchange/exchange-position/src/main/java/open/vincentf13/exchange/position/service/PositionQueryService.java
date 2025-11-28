package open.vincentf13.exchange.position.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.common.sdk.enums.PositionIntentType;
import open.vincentf13.exchange.common.sdk.enums.PositionStatus;
import open.vincentf13.exchange.position.infra.persistence.po.PositionPO;
import open.vincentf13.exchange.position.infra.persistence.repository.PositionRepository;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionIntentRequest;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionIntentResponse;
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
        BigDecimal existing = activePosition
                .map(position -> position.getQuantity())
                .orElse(BigDecimal.ZERO);
        PositionIntentType intentType = activePosition
                .map(position -> position.evaluateIntent(request.side(), request.quantity()))
                .orElse(PositionIntentType.INCREASE);
        return PositionIntentResponse.of(intentType, existing);
    }
}
