package open.vincentf13.exchange.position.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.position.infra.persistence.repository.PositionRepository;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionIntentRequest;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionIntentResponse;
import open.vincentf13.exchange.position.sdk.rest.api.enums.PositionIntentType;
import open.vincentf13.exchange.position.sdk.rest.api.enums.PositionSide;
import open.vincentf13.sdk.core.OpenValidator;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Validated
public class PositionQueryService {

    private final PositionRepository positionRepository;

    public PositionIntentResponse determineIntent(@NotNull @Valid PositionIntentRequest request) {
        OpenValidator.validateOrThrow(request);
        var activePosition = positionRepository.findOne(open.vincentf13.exchange.position.domain.model.Position.builder()
                        .userId(request.userId())
                        .instrumentId(request.instrumentId())
                        .status("ACTIVE")
                        .build());
        BigDecimal existing = activePosition
                .map(position -> position.getQuantity())
                .orElse(BigDecimal.ZERO);
        PositionIntentType intentType = activePosition
                .map(position -> position.evaluateIntent(request.side(), request.quantity()))
                .orElse(PositionIntentType.INCREASE);
        return PositionIntentResponse.of(intentType, existing);
    }
}
