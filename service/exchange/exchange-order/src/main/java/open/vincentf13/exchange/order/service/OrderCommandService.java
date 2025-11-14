package open.vincentf13.exchange.order.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.exchange.order.domain.model.Order;
import open.vincentf13.exchange.order.domain.model.OrderErrorCode;
import open.vincentf13.exchange.order.domain.service.OrderDomainService;
import open.vincentf13.exchange.order.infra.messaging.publisher.OrderEventPublisher;
import open.vincentf13.exchange.order.infra.persistence.repository.OrderRepository;
import open.vincentf13.exchange.order.sdk.rest.api.dto.OrderCreateRequest;
import open.vincentf13.exchange.order.sdk.rest.api.dto.OrderResponse;
import open.vincentf13.exchange.order.sdk.rest.api.dto.OrderStatus;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionIntentRequest;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionIntentResponse;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionIntentType;
import open.vincentf13.exchange.position.sdk.rest.client.ExchangePositionClient;
import open.vincentf13.sdk.auth.jwt.OpenJwtLoginUserInfo;
import open.vincentf13.sdk.core.OpenMapstruct;
import open.vincentf13.sdk.core.exception.OpenServiceException;
import open.vincentf13.sdk.spring.mvc.client.OpenApiClientInvoker;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderCommandService {

    private final OrderDomainService orderDomainService;
    private final OrderRepository orderRepository;
    private final OrderEventPublisher orderEventPublisher;
    private final ExchangePositionClient exchangePositionClient;
    private final TransactionTemplate transactionTemplate;

    public OrderResponse createOrder(OrderCreateRequest request) {
        Long userId = currentUserId();
        try {
            Order order = orderDomainService.createNewOrder(userId, request);
            PositionIntentType intentType = determineIntent(userId, request);

            transactionTemplate.executeWithoutResult(status -> {
                if (intentType != null && intentType.requiresPositionReservation()) {
                    orderEventPublisher.publishPositionReserveRequested(order, intentType);
                } else {
                    markSubmitted(order);
                    orderEventPublisher.publishOrderSubmitted(order);
                }
                orderRepository.insert(order);
            });

            return OpenMapstruct.map(order, OrderResponse.class);
        } catch (DuplicateKeyException ex) {
            log.info("Duplicate order insert for user {} clientOrderId {}", userId, request.clientOrderId());
            return orderRepository.findByUserIdAndClientOrderId(userId, request.clientOrderId())
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
                () -> exchangePositionClient.determineIntent(new PositionIntentRequest(userId, request.instrumentId(), request.side(), request.quantity())),
                errorMsg -> OpenServiceException.of(OrderErrorCode.ORDER_STATE_CONFLICT, "Unable to determine position intent. " + errorMsg.describe())
        );
        PositionIntentType intentType = response.intentType();
        if (intentType == null) {
            throw OpenServiceException.of(OrderErrorCode.ORDER_STATE_CONFLICT,
                    "Position intent type is null for instrument=" + request.instrumentId());
        }
        return intentType;
    }

    private void markSubmitted(Order order) {
        Instant now = Instant.now();
        order.markStatus(OrderStatus.SUBMITTED, now);
        order.incrementVersion();
    }
}
