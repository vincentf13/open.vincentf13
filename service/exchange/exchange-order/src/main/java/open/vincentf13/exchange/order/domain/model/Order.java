package open.vincentf13.exchange.order.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import open.vincentf13.exchange.order.infra.OrderErrorCode;
import open.vincentf13.exchange.order.sdk.rest.api.enums.OrderSide;
import open.vincentf13.exchange.order.sdk.rest.api.enums.OrderStatus;
import open.vincentf13.exchange.order.sdk.rest.api.enums.OrderTimeInForce;
import open.vincentf13.exchange.order.sdk.rest.api.enums.OrderType;
import open.vincentf13.exchange.position.sdk.rest.api.enums.PositionIntentType;
import open.vincentf13.sdk.core.OpenBigDecimal;
import open.vincentf13.sdk.core.exception.OpenServiceException;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    private static final String DEFAULT_SOURCE = "WEB";

    private Long orderId;
    private Long userId;
    private Long instrumentId;
    private String clientOrderId;
    private OrderSide side;
    private PositionIntentType intent;
    private BigDecimal closeCostPrice;
    private OrderType type;
    private OrderStatus status;
    private OrderTimeInForce timeInForce;
    private BigDecimal price;
    private BigDecimal stopPrice;
    private BigDecimal quantity;
    private BigDecimal filledQuantity;
    private BigDecimal remainingQuantity;
    private BigDecimal avgFillPrice;
    private BigDecimal fee;
    private String source;
    private Integer version;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant submittedAt;
    private Instant filledAt;

    public void markStatus(OrderStatus newStatus, Instant updatedAt) {
        this.status = newStatus;
        this.updatedAt = updatedAt;
    }

    public void incrementVersion() {
        if (version == null) {
            version = 0;
        }
        this.version = this.version + 1;
    }


    public static Order createNew(Long userId, open.vincentf13.exchange.order.sdk.rest.api.dto.OrderCreateRequest request) {
        if (userId == null) {
            throw OpenServiceException.of(OrderErrorCode.ORDER_VALIDATION_FAILED, "Missing userId");
        }
        validateRequest(request);
        Instant now = Instant.now();
        BigDecimal normalizedQty = OpenBigDecimal.normalizeDecimal(request.quantity());
        BigDecimal normalizedPrice = OpenBigDecimal.normalizeDecimal(request.price());
        BigDecimal normalizedStopPrice = OpenBigDecimal.normalizeDecimal(request.stopPrice());
        OrderTimeInForce timeInForce = request.timeInForce() != null ? request.timeInForce() : OrderTimeInForce.GTC;
        return Order.builder()
                .userId(userId)
                .instrumentId(request.instrumentId())
                .clientOrderId(trimToNull(request.clientOrderId()))
                .side(request.side())
                .intent(null)
                .closeCostPrice(null)
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
                .source(trimToDefault(request.source()))
                .version(0)
                .createdAt(now)
                .updatedAt(now)
                .submittedAt(null)
                .filledAt(null)
                .build();
    }



    private static void validateRequest(open.vincentf13.exchange.order.sdk.rest.api.dto.OrderCreateRequest request) {
        if (request == null) {
            throw OpenServiceException.of(OrderErrorCode.ORDER_VALIDATION_FAILED, "request cannot be null");
        }
        if (requiresPrice(request.type()) && request.price() == null) {
            throw OpenServiceException.of(OrderErrorCode.ORDER_VALIDATION_FAILED, "price is required for type " + request.type());
        }
        if (requiresStopPrice(request.type()) && request.stopPrice() == null) {
            throw OpenServiceException.of(OrderErrorCode.ORDER_VALIDATION_FAILED, "stopPrice is required for type " + request.type());
        }
        if (request.quantity() == null || OpenBigDecimal.isNonPositive(request.quantity())) {
            throw OpenServiceException.of(OrderErrorCode.ORDER_VALIDATION_FAILED, "quantity must be positive");
        }
        if (request.price() != null && OpenBigDecimal.isNonPositive(request.price())) {
            throw OpenServiceException.of(OrderErrorCode.ORDER_VALIDATION_FAILED, "price must be positive");
        }
        if (request.stopPrice() != null && OpenBigDecimal.isNonPositive(request.stopPrice())) {
            throw OpenServiceException.of(OrderErrorCode.ORDER_VALIDATION_FAILED, "stopPrice must be positive");
        }
    }

    private static boolean requiresPrice(OrderType type) {
        return type == OrderType.LIMIT || type == OrderType.STOP_LIMIT;
    }

    private static boolean requiresStopPrice(OrderType type) {
        return type == OrderType.STOP_LIMIT || type == OrderType.STOP_MARKET;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String trimToDefault(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? DEFAULT_SOURCE : trimmed;
    }
}
