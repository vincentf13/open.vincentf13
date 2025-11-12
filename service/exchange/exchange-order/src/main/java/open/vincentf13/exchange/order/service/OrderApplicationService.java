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
public class OrderApplicationService {

    private static final String USER_CANCEL_REASON = "USER_REQUEST";

    private final OrderDomainService orderDomainService;
    private final OrderRepository orderRepository;
    private final OrderEventPublisher orderEventPublisher;
    private final OrderAssembler orderAssembler;
    private final TransactionTemplate transactionTemplate;

    public OrderResponse createOrder(OrderCreateRequest request) {
        Long userId = currentUserId();
        try {
            Order order = transactionTemplate.execute(status -> {
                Order entity = orderDomainService.createNewOrder(userId, request);
                orderRepository.insert(entity);
                return entity;
            });

            if (order == null) {
                throw OpenServiceException.of(OrderErrorCode.ORDER_STATE_CONFLICT, "Order creation returned null result");
            }

            orderEventPublisher.publishOrderSubmitted(order);
            return orderAssembler.toResponse(order);
        } catch (DuplicateKeyException ex) {
            log.info("Duplicate order insert for user {} clientOrderId {}", userId, request.clientOrderId());
            return orderRepository.findByUserIdAndClientOrderId(userId, request.clientOrderId())
                    .map(orderAssembler::toResponse)
                    .orElseThrow(() -> OpenServiceException.of(OrderErrorCode.ORDER_STATE_CONFLICT,
                            "Duplicate order detected but existing record not found"));
        }
    }

    public void requestCancel(Long orderId) {
        Long userId = currentUserId();
        CancelResult result = transactionTemplate.execute(status -> {
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> OpenServiceException.of(OrderErrorCode.ORDER_NOT_FOUND,
                            "Order not found. id=" + orderId));
            ensureOwner(userId, order);
            orderDomainService.ensureCancelable(order);
            Instant now = Instant.now();
            boolean updated = orderRepository.updateStatus(order.getId(), userId, OrderStatus.CANCEL_REQUESTED, now,
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
                    "Order does not belong to current user. id=" + order.getId());
        }
    }

    private record CancelResult(Order order, Instant requestedAt) { }
}
