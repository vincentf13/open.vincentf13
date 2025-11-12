package open.vincentf13.exchange.order.domain.service;

import lombok.extern.slf4j.Slf4j;
import open.vincentf13.exchange.order.domain.model.Order;
import open.vincentf13.exchange.order.domain.model.OrderErrorCode;
import open.vincentf13.exchange.order.sdk.rest.api.dto.OrderCreateRequest;
import open.vincentf13.exchange.order.sdk.rest.api.dto.OrderSide;
import open.vincentf13.exchange.order.sdk.rest.api.dto.OrderStatus;
import open.vincentf13.exchange.order.sdk.rest.api.dto.OrderTimeInForce;
import open.vincentf13.exchange.order.sdk.rest.api.dto.OrderType;
import open.vincentf13.sdk.core.exception.OpenServiceException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

@Service
@Slf4j
public class OrderDomainService {

    private static final Set<OrderStatus> CANCEL_NOT_ALLOWED = EnumSet.of(
            OrderStatus.CANCELLED,
            OrderStatus.CANCEL_REQUESTED,
            OrderStatus.FILLED,
            OrderStatus.REJECTED,
            OrderStatus.FAILED,
            OrderStatus.EXPIRED
    );

    private static final String DEFAULT_SOURCE = "WEB";

    public Order createNewOrder(Long userId, OrderCreateRequest request) {
        if (userId == null) {
            throw OpenServiceException.of(OrderErrorCode.ORDER_VALIDATION_FAILED, "Missing userId");
        }
        validateRequest(request);
        Instant now = Instant.now();
        BigDecimal normalizedQty = normalizeDecimal(request.quantity());
        BigDecimal normalizedPrice = normalizeDecimal(request.price());
        BigDecimal normalizedStopPrice = normalizeDecimal(request.stopPrice());
        OrderTimeInForce timeInForce = request.timeInForce() != null
                ? request.timeInForce()
                : OrderTimeInForce.GTC;
        return Order.builder()
                .userId(userId)
                .instrumentId(request.instrumentId())
                .clientOrderId(trimToNull(request.clientOrderId()))
                .side(request.side())
                .type(request.type())
                .status(OrderStatus.PENDING)
                .timeInForce(timeInForce)
                .price(normalizedPrice)
                .stopPrice(normalizedStopPrice)
                .quantity(normalizedQty)
                .filledQuantity(BigDecimal.ZERO)
                .remainingQuantity(normalizedQty)
                .avgFillPrice(null)
                .fee(BigDecimal.ZERO)
                .source(StringUtils.hasText(request.source()) ? request.source().trim() : DEFAULT_SOURCE)
                .version(0)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public void ensureCancelable(Order order) {
        if (order == null) {
            throw OpenServiceException.of(OrderErrorCode.ORDER_NOT_FOUND, "Order is null");
        }
        if (CANCEL_NOT_ALLOWED.contains(order.getStatus())) {
            throw OpenServiceException.of(OrderErrorCode.ORDER_STATUS_NOT_CANCELABLE,
                    "Order status %s cannot be cancelled".formatted(order.getStatus()));
        }
    }

    private void validateRequest(OrderCreateRequest request) {
        if (request == null) {
            throw OpenServiceException.of(OrderErrorCode.ORDER_VALIDATION_FAILED, "request cannot be null");
        }
        if (requiresPrice(request.type()) && request.price() == null) {
            throw OpenServiceException.of(OrderErrorCode.ORDER_VALIDATION_FAILED, "price is required for type " + request.type());
        }
        if (requiresStopPrice(request.type()) && request.stopPrice() == null) {
            throw OpenServiceException.of(OrderErrorCode.ORDER_VALIDATION_FAILED, "stopPrice is required for type " + request.type());
        }
        if (request.quantity() == null || isNonPositive(request.quantity())) {
            throw OpenServiceException.of(OrderErrorCode.ORDER_VALIDATION_FAILED, "quantity must be positive");
        }
        if (request.price() != null && isNonPositive(request.price())) {
            throw OpenServiceException.of(OrderErrorCode.ORDER_VALIDATION_FAILED, "price must be positive");
        }
        if (request.stopPrice() != null && isNonPositive(request.stopPrice())) {
            throw OpenServiceException.of(OrderErrorCode.ORDER_VALIDATION_FAILED, "stopPrice must be positive");
        }
    }

    private boolean requiresPrice(OrderType type) {
        return type == OrderType.LIMIT || type == OrderType.STOP_LIMIT;
    }

    private boolean requiresStopPrice(OrderType type) {
        return type == OrderType.STOP_LIMIT || type == OrderType.STOP_MARKET;
    }

    private boolean isNonPositive(BigDecimal value) {
        return value == null || value.signum() <= 0;
    }

    private BigDecimal normalizeDecimal(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
