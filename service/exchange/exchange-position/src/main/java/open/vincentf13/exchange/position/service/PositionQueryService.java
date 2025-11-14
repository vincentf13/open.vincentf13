package open.vincentf13.exchange.position.service;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.position.domain.model.Position;
import open.vincentf13.exchange.position.infra.repository.PositionRepository;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionIntentRequest;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionIntentResponse;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionIntentType;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionResponse;
import open.vincentf13.sdk.core.OpenMapstruct;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PositionQueryService {

    private final PositionRepository positionRepository;

    public List<PositionResponse> list(Long userId) {
        return positionRepository.findByUser(userId).stream()
                .map(position -> OpenMapstruct.map(position, PositionResponse.class))
                .toList();
    }

    public PositionIntentResponse determineIntent(PositionIntentRequest request) {
        PositionIntentValidator.validate(request);
        var activePosition = positionRepository.findActive(request.userId(), request.instrumentId());
        BigDecimal existing = activePosition
                .map(Position::getQuantity)
                .orElse(BigDecimal.ZERO);
        PositionIntentType intentType = activePosition
                .map(position -> position.evaluateIntent(request.orderSide(), request.quantity()))
                .orElse(PositionIntentType.INCREASE);
        return PositionIntentResponse.of(intentType, existing);
    }
}
