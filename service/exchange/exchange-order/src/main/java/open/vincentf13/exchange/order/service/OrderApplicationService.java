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
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

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
        OrderCreationResult result = transactionTemplate.execute(status -> {
            Optional<Order> duplicated = findExistingOrder(userId, request.clientOrderId());
            if (duplicated.isPresent()) {
                log.info("Idempotent hit for user {} clientOrderId {}", userId, request.clientOrderId());
                return OrderCreationResult.existing(duplicated.get());
            }
            Order order = orderDomainService.createNewOrder(userId, request);
            orderRepository.insert(order);
            return OrderCreationResult.created(order);
        });

        if (result == null) {
            throw OpenServiceException.of(OrderErrorCode.ORDER_STATE_CONFLICT, "Order creation returned null result");
        }
        if (result.created()) {
            orderEventPublisher.publishOrderSubmitted(result.order());
        }
        return orderAssembler.toResponse(result.order());
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

    private Optional<Order> findExistingOrder(Long userId, String clientOrderId) {
        if (!StringUtils.hasText(clientOrderId)) {
            return Optional.empty();
        }
        return orderRepository.findByUserIdAndClientOrderId(userId, clientOrderId.trim());
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

    private record OrderCreationResult(Order order, boolean created) {
        static OrderCreationResult created(Order order) {
            return new OrderCreationResult(order, true);
        }

        static OrderCreationResult existing(Order order) {
            return new OrderCreationResult(order, false);
        }
    }

    private record CancelResult(Order order, Instant requestedAt) { }
}
