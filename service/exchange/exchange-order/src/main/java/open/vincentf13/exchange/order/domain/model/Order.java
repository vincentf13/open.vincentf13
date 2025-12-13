package open.vincentf13.exchange.order.domain.model;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import open.vincentf13.exchange.common.sdk.constants.ValidationConstant;
import open.vincentf13.exchange.common.sdk.enums.OrderSide;
import open.vincentf13.exchange.common.sdk.enums.OrderStatus;
import open.vincentf13.exchange.common.sdk.enums.OrderType;
import open.vincentf13.exchange.common.sdk.enums.PositionIntentType;
import open.vincentf13.exchange.order.infra.OrderErrorCode;
import open.vincentf13.exchange.order.sdk.rest.dto.OrderCreateRequest;
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
    @NotNull
    private OrderType type;
    private BigDecimal price;
    @NotNull
    @DecimalMin(value = ValidationConstant.Names.QUANTITY_MIN, inclusive = true)
    private BigDecimal quantity;
    private PositionIntentType intent;
    @DecimalMin(value = ValidationConstant.Names.NON_NEGATIVE, inclusive = true)
    private BigDecimal filledQuantity;
    @DecimalMin(value = ValidationConstant.Names.NON_NEGATIVE, inclusive = true)
    private BigDecimal remainingQuantity;
    @DecimalMin(value = ValidationConstant.Names.NON_NEGATIVE, inclusive = true)
    private BigDecimal avgFillPrice;
    private BigDecimal fee;
    @NotNull
    private OrderStatus status;
    private String rejectedReason;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant submittedAt;
    private Instant filledAt;
    private Instant cancelledAt;
    private Integer version;
    
    public static Order createNew(Long userId,
                                  OrderCreateRequest request) {
        if (userId == null) {
            throw OpenException.of(OrderErrorCode.ORDER_VALIDATION_FAILED, Map.of("field", "userId"));
        }
        validateRequest(request);
        BigDecimal normalizedQty = OpenBigDecimal.normalizeDecimal(request.quantity());
        BigDecimal normalizedPrice = OpenBigDecimal.normalizeDecimal(request.price());
        return Order.builder()
                    .userId(userId)
                    .instrumentId(request.instrumentId())
                    .clientOrderId(trimToNull(request.clientOrderId()))
                    .side(request.side())
                    .intent(null)
                    .type(request.type())
                    .status(OrderStatus.CREATED)
                    .price(normalizedPrice)
                    .quantity(normalizedQty)
                    .filledQuantity(BigDecimal.ZERO)
                    .remainingQuantity(normalizedQty)
                    .avgFillPrice(null)
                    .fee(null)
                    .rejectedReason(null)
                    .version(0)
                    .submittedAt(null)
                    .filledAt(null)
                    .cancelledAt(null)
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
    
    public void markStatus(OrderStatus newStatus,
                           Instant updatedAt) {
        this.status = newStatus;
        this.updatedAt = updatedAt;
    }
    
    public void incrementVersion() {
        if (version == null) {
            version = 0;
        }
        this.version = this.version + 1;
    }
}
