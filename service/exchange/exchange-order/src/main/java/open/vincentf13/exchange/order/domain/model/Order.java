package open.vincentf13.exchange.order.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import open.vincentf13.exchange.order.infra.OrderErrorCode;
import open.vincentf13.exchange.order.sdk.enums.OrderCreateRequest;
import open.vincentf13.exchange.common.sdk.enums.OrderSide;
import open.vincentf13.exchange.common.sdk.enums.OrderStatus;
import open.vincentf13.exchange.common.sdk.enums.OrderType;
import open.vincentf13.exchange.common.sdk.enums.PositionIntentType;
import open.vincentf13.sdk.core.OpenBigDecimal;
import open.vincentf13.sdk.core.exception.OpenException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    private Long orderId;
    @NotNull
    private Long userId;
    @NotNull
    private Long instrumentId;
    private String clientOrderId;
    @NotNull
    private OrderSide side;
    private PositionIntentType intent;
    @DecimalMin(value = "0", inclusive = true)
    private BigDecimal closeCostPrice;
    @NotNull
    private OrderType type;
    @NotNull
    private OrderStatus status;
    @DecimalMin(value = "0", inclusive = false)
    private BigDecimal price;
    @NotNull
    @DecimalMin(value = "0.00000001", inclusive = true)
    private BigDecimal quantity;
    @DecimalMin(value = "0", inclusive = true)
    private BigDecimal filledQuantity;
    
    @DecimalMin(value = "0", inclusive = true)
    private BigDecimal remainingQuantity;
    @DecimalMin(value = "0", inclusive = true)
    private BigDecimal avgFillPrice;
    @DecimalMin(value = "0", inclusive = true)
    private BigDecimal fee;
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


    public static Order createNew(Long userId, OrderCreateRequest request) {
        if (userId == null) {
            throw OpenException.of(OrderErrorCode.ORDER_VALIDATION_FAILED, Map.of("field", "userId"));
        }
        validateRequest(request);
        Instant now = Instant.now();
        BigDecimal normalizedQty = OpenBigDecimal.normalizeDecimal(request.quantity());
        BigDecimal normalizedPrice = OpenBigDecimal.normalizeDecimal(request.price());
        return Order.builder()
                .userId(userId)
                .instrumentId(request.instrumentId())
                .clientOrderId(trimToNull(request.clientOrderId()))
                .side(request.side())
                .intent(null)
                .closeCostPrice(null)
                .type(request.type())
                .status(OrderStatus.PENDING)
                .price(normalizedPrice)
                .quantity(normalizedQty)
                .filledQuantity(BigDecimal.ZERO)
                .remainingQuantity(normalizedQty)
                .avgFillPrice(null)
                .fee(BigDecimal.ZERO)
                .version(0)
                .submittedAt(null)
                .filledAt(null)
                .build();
    }



    private static void validateRequest(OrderCreateRequest request) {
        if (request == null) {
            throw OpenException.of(OrderErrorCode.ORDER_VALIDATION_FAILED, Map.of("field", "request"));
        }
        if (requiresPrice(request.type()) && request.price() == null) {
            throw OpenException.of(OrderErrorCode.ORDER_VALIDATION_FAILED,
                                   Map.of("field", "price", "orderType", request.type()));
        }
        if (request.quantity() == null || OpenBigDecimal.isNonPositive(request.quantity())) {
            throw OpenException.of(OrderErrorCode.ORDER_VALIDATION_FAILED, Map.of("field", "quantity"));
        }
        if (request.price() != null && OpenBigDecimal.isNonPositive(request.price())) {
            throw OpenException.of(OrderErrorCode.ORDER_VALIDATION_FAILED, Map.of("field", "price"));
        }
    }

    private static boolean requiresPrice(OrderType type) {
        return type == OrderType.LIMIT || type == OrderType.STOP_LIMIT;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
