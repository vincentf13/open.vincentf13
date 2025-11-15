package open.vincentf13.exchange.position.service;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.position.domain.model.PositionSide;
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
        validate(request);
        var activePosition = positionRepository.findActive(request.userId(), request.instrumentId());
        BigDecimal existing = activePosition
                .map(position -> position.getQuantity())
                .orElse(BigDecimal.ZERO);
        PositionIntentType intentType = activePosition
                .map(position -> position.evaluateIntent(PositionSide.fromOrderSide(request.orderSide()), request.quantity()))
                .orElse(PositionIntentType.INCREASE);
        return PositionIntentResponse.of(intentType, existing);
    }

    private void validate(PositionIntentRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("PositionIntentRequest must not be null");
        }
        if (request.userId() == null) {
            throw new IllegalArgumentException("userId is required");
        }
        if (request.instrumentId() == null) {
            throw new IllegalArgumentException("instrumentId is required");
        }
        if (request.orderSide() == null) {
            throw new IllegalArgumentException("orderSide is required");
        }
        if (request.quantity() == null) {
            throw new IllegalArgumentException("quantity is required");
        }
        if (request.quantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
    }
}
