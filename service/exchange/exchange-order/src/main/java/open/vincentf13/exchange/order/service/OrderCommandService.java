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
import open.vincentf13.sdk.core.OpenMapstruct;
import open.vincentf13.sdk.auth.jwt.OpenJwtLoginUserInfo;
import open.vincentf13.sdk.core.exception.OpenServiceException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderCommandService {

    private static final String USER_CANCEL_REASON = "USER_REQUEST";

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

    public void requestCancel(Long orderId) {
        Long userId = currentUserId();
        CancelResult result = transactionTemplate.execute(status -> {
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> OpenServiceException.of(OrderErrorCode.ORDER_NOT_FOUND,
                            "Order not found. orderId=" + orderId));
            ensureOwner(userId, order);
            orderDomainService.ensureCancelable(order);
            Instant now = Instant.now();
            boolean updated = orderRepository.updateStatus(order.getOrderId(), userId, OrderStatus.CANCEL_REQUESTED, now,
                    Optional.ofNullable(order.getVersion()).orElse(0));
            if (!updated) {
                throw OpenServiceException.of(OrderErrorCode.ORDER_STATE_CONFLICT,
                        "Failed to mark cancel, concurrent modification detected");
            }
            order.markStatus(OrderStatus.CANCEL_REQUESTED, now);
            order.incrementVersion();
            return new CancelResult(order, now);
        });
        if (result == null) {
            throw OpenServiceException.of(OrderErrorCode.ORDER_STATE_CONFLICT, "Cancel transaction returned null result");
        }
        orderEventPublisher.publishOrderCancelRequested(result.order(), result.requestedAt(), USER_CANCEL_REASON);
    }

    private Long currentUserId() {
        return OpenJwtLoginUserInfo.currentUserIdOrThrow(() ->
                OpenServiceException.of(OrderErrorCode.ORDER_NOT_FOUND, "No authenticated user context"));
    }

    private void ensureOwner(Long userId, Order order) {
        if (!userId.equals(order.getUserId())) {
            throw OpenServiceException.of(OrderErrorCode.ORDER_NOT_OWNED,
                    "Order does not belong to current user. orderId=" + order.getOrderId());
        }
    }

    private PositionIntentType determineIntent(Long userId, OrderCreateRequest request) {
        try {
            PositionIntentResponse response = exchangePositionClient.determineIntent(
                    new PositionIntentRequest(userId, request.instrumentId(), request.side(), request.quantity())
            ).data();
            if (response == null) {
                log.warn("Position intent response is null for user {} instrument {}", userId, request.instrumentId());
                throw OpenServiceException.of(OrderErrorCode.ORDER_STATE_CONFLICT,
                        "Unable to determine position intent due to null response");
            }
            return response.intentType();
        } catch (Exception ex) {
            log.warn("Fallback to INCREASE intent for user {} instrument {} due to {}", userId, request.instrumentId(), ex.getMessage());
            throw ex;
        }
    }

    private record CancelResult(Order order, Instant requestedAt) { }

    private void markSubmitted(Order order) {
        Instant now = Instant.now();
        order.markStatus(OrderStatus.SUBMITTED, now);
        order.incrementVersion();
    }
}
