package open.vincentf13.exchange.position.service;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.order.sdk.rest.api.dto.OrderSide;
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
        PositionIntentType intentType = computeIntent(request);
        BigDecimal existing = positionRepository.findActive(request.userId(), request.instrumentId())
                .map(Position::getQuantity)
                .orElse(BigDecimal.ZERO);
        return PositionIntentResponse.of(intentType, existing);
    }

    private PositionIntentType computeIntent(PositionIntentRequest request) {
        return positionRepository.findActive(request.userId(), request.instrumentId())
                .map(position -> resolveIntent(position, request.orderSide(), request.quantity()))
                .orElse(PositionIntentType.INCREASE);
    }

    private PositionIntentType resolveIntent(Position position, OrderSide orderSide, BigDecimal requestedQty) {
        if (position.getSide() == null || orderSide == null) {
            return PositionIntentType.INCREASE;
        }
        if (!position.isOpposite(orderSide)) {
            return PositionIntentType.INCREASE;
        }
        BigDecimal quantity = position.getQuantity() == null ? BigDecimal.ZERO : position.getQuantity();
        if (requestedQty == null) {
            return PositionIntentType.REDUCE;
        }
        int compare = quantity.compareTo(requestedQty);
        if (compare > 0) {
            return PositionIntentType.REDUCE;
        }
        return PositionIntentType.CLOSE;
    }
}
