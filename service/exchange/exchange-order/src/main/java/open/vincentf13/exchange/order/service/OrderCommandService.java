package open.vincentf13.exchange.order.service;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.exchange.order.domain.model.Order;
import open.vincentf13.exchange.order.infra.OrderErrorCode;
import open.vincentf13.exchange.order.infra.messaging.publisher.OrderEventPublisher;
import open.vincentf13.exchange.order.infra.persistence.repository.OrderRepository;
import open.vincentf13.exchange.order.sdk.rest.api.dto.OrderCreateRequest;
import open.vincentf13.exchange.order.sdk.rest.api.dto.OrderResponse;
import open.vincentf13.exchange.order.sdk.rest.api.enums.OrderSide;
import open.vincentf13.exchange.order.sdk.rest.api.enums.OrderStatus;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionIntentRequest;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionIntentResponse;
import open.vincentf13.exchange.position.sdk.rest.api.enums.PositionIntentType;
import open.vincentf13.exchange.position.sdk.rest.api.enums.PositionSide;
import open.vincentf13.exchange.position.sdk.rest.client.ExchangePositionClient;
import open.vincentf13.sdk.auth.jwt.OpenJwtLoginUserInfo;
import open.vincentf13.sdk.core.OpenMapstruct;
import open.vincentf13.sdk.core.exception.OpenServiceException;
import open.vincentf13.sdk.spring.cloud.openfeign.OpenApiClientInvoker;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.annotation.Validated;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
@Validated
public class OrderCommandService {

    private final OrderRepository orderRepository;
    private final OrderEventPublisher orderEventPublisher;
    private final ExchangePositionClient exchangePositionClient;
    private final TransactionTemplate transactionTemplate;

    public OrderResponse createOrder(@Valid OrderCreateRequest request) {
        Long userId = currentUserId();
        try {
            Order order = Order.createNew(userId, request);
            PositionIntentType intentType = determineIntent(userId, request);
            order.setIntent(intentType);

            transactionTemplate.executeWithoutResult(status -> {
                if (order.getIntent() != null && order.getIntent().requiresPositionReservation()) {
                    orderEventPublisher.publishPositionReserveRequested(order, order.getIntent());
                } else {
                    markSubmitted(order);
                    orderEventPublisher.publishOrderSubmitted(order);
                }
                orderRepository.insertSelective(order);
            });

            return OpenMapstruct.map(order, OrderResponse.class);
        } catch (DuplicateKeyException ex) {
            log.info("Duplicate order insert for user {} clientOrderId {}", userId, request.clientOrderId());
            return orderRepository.findOne(Order.builder()
                            .userId(userId)
                            .clientOrderId(request.clientOrderId())
                            .build())
                    .map(o -> OpenMapstruct.map(o, OrderResponse.class))
                    .orElseThrow(() -> OpenServiceException.of(OrderErrorCode.ORDER_STATE_CONFLICT,
                            "Duplicate order detected but existing record not found"));
        }
    }

    private Long currentUserId() {
        return OpenJwtLoginUserInfo.currentUserIdOrThrow(() ->
                OpenServiceException.of(OrderErrorCode.ORDER_NOT_FOUND, "No authenticated user context"));
    }

    private PositionIntentType determineIntent(Long userId, OrderCreateRequest request) {
        PositionIntentResponse response = OpenApiClientInvoker.call(
                () -> exchangePositionClient.determineIntent(new PositionIntentRequest(userId, request.instrumentId(), toPositionSide(request.side()), request.quantity())),
                msg -> OpenServiceException.of(OrderErrorCode.ORDER_STATE_CONFLICT,
                        "Failed to determine position intent for user %s instrument %s: %s".formatted(userId, request.instrumentId(), msg))
        );
        PositionIntentType intentType = response.intentType();
        if (intentType == null) {
            throw OpenServiceException.of(OrderErrorCode.ORDER_STATE_CONFLICT,
                    "Position intent type is null for instrument=" + request.instrumentId());
        }
        return intentType;
    }

    private PositionSide toPositionSide(OrderSide orderSide) {
        if (orderSide == null) {
            return null;
        }
        return orderSide == open.vincentf13.exchange.order.sdk.rest.api.enums.OrderSide.BUY
                ? PositionSide.LONG
                : PositionSide.SHORT;
    }

    private void markSubmitted(Order order) {
        Instant now = Instant.now();
        order.markStatus(OrderStatus.SUBMITTED, now);
        order.incrementVersion();
    }
}
