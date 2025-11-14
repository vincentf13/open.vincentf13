package open.vincentf13.exchange.position.service;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.position.infra.persistence.repository.PositionRepository;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionIntentRequest;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionIntentResponse;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionIntentType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class PositionQueryService {

    private final PositionRepository positionRepository;

    public PositionIntentResponse determineIntent(PositionIntentRequest request) {
        PositionIntentValidator.validate(request);
        var activePosition = positionRepository.findActive(request.userId(), request.instrumentId());
        BigDecimal existing = activePosition
                .map(position -> position.getQuantity())
                .orElse(BigDecimal.ZERO);
        PositionIntentType intentType = activePosition
                .map(position -> position.evaluateIntent(request.orderSide(), request.quantity()))
                .orElse(PositionIntentType.INCREASE);
        return PositionIntentResponse.of(intentType, existing);
    }
}
