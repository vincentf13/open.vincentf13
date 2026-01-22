package open.vincentf13.exchange.order.domain.model;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import open.vincentf13.exchange.common.sdk.constants.ScaleConstant;
import open.vincentf13.exchange.common.sdk.constants.ValidationConstant;
import open.vincentf13.exchange.common.sdk.enums.OrderSide;
import open.vincentf13.exchange.common.sdk.enums.OrderStatus;
import open.vincentf13.exchange.common.sdk.enums.OrderType;
import open.vincentf13.exchange.common.sdk.enums.PositionIntentType;
import open.vincentf13.exchange.order.infra.OrderErrorCode;
import open.vincentf13.exchange.order.sdk.rest.dto.OrderCreateRequest;
import open.vincentf13.sdk.core.exception.OpenException;
import open.vincentf13.sdk.core.validator.Id;
import open.vincentf13.sdk.core.values.OpenDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {

  @NotNull(groups = Id.class)
  private Long orderId;
  @NotNull private Long userId;
  @NotNull private Long instrumentId;
  private String clientOrderId;
  @NotNull private OrderSide side;
  @NotNull private OrderType type;
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
  @NotNull private OrderStatus status;
  private String rejectedReason;
  private Instant createdAt;
  private Instant updatedAt;
  private Instant submittedAt;
  private Instant filledAt;
  private Instant cancelledAt;
  private Integer version;

  public static Order initWithVaildate(Long userId, OrderCreateRequest request) {
    if (userId == null) {
      throw OpenException.of(OrderErrorCode.ORDER_VALIDATION_FAILED, Map.of("field", "userId"));
    }
    validateRequest(request);
    BigDecimal normalizedQty = OpenDecimal.normalizeDecimal(request.quantity());
    BigDecimal normalizedPrice = OpenDecimal.normalizeDecimal(request.price());
    return Order.builder()
        .orderId(null)
        .userId(userId)
        .instrumentId(request.instrumentId())
        .clientOrderId(trimToNull(request.clientOrderId()))
        .side(request.side())
        .type(request.type())
        .price(normalizedPrice)
        .quantity(normalizedQty)
        .intent(null)
        .filledQuantity(BigDecimal.ZERO)
        .remainingQuantity(normalizedQty)
        .avgFillPrice(null)
        .fee(null)
        .status(OrderStatus.CREATED)
        .rejectedReason(null)
        .createdAt(null)
        .updatedAt(null)
        .submittedAt(null)
        .filledAt(null)
        .cancelledAt(null)
        .version(0)
        .build();
  }

  private static void validateRequest(OrderCreateRequest request) {
    if (request == null) {
      throw OpenException.of(OrderErrorCode.ORDER_VALIDATION_FAILED, Map.of("field", "request"));
    }
    String clientOrderId = request.clientOrderId();
    if (clientOrderId == null || clientOrderId.trim().isEmpty()) {
      clientOrderId = UUID.randomUUID().toString();
      throw OpenException.of(
          OrderErrorCode.ORDER_VALIDATION_FAILED,
          Map.of("field", "clientOrderId", "reason", "required"));
    }
    if (clientOrderId.length() > OrderCreateRequest.CLIENT_ORDER_ID_MAX_LENGTH) {
      throw OpenException.of(
          OrderErrorCode.ORDER_VALIDATION_FAILED,
          Map.of("field", "clientOrderId", "reason", "lengthExceeded"));
    }
    if (requiresPrice(request.type()) && request.price() == null) {
      throw OpenException.of(
          OrderErrorCode.ORDER_VALIDATION_FAILED,
          Map.of("field", "price", "orderType", request.type()));
    }

    if (request.quantity() == null || request.quantity().signum() <= 0) {
      throw OpenException.of(OrderErrorCode.ORDER_VALIDATION_FAILED, Map.of("field", "quantity"));
    }
    if (request.price() != null && request.price().signum() <= 0) {
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

  public void markStatus(OrderStatus newStatus, Instant updatedAt) {
    this.status = newStatus;
    this.updatedAt = updatedAt;
  }

  public void onFundsFrozen(Instant eventTime) {
    if (this.status != OrderStatus.FREEZING_MARGIN) {
      return;
    }
    this.status = OrderStatus.NEW;
    this.submittedAt = eventTime;
  }

  public void onFundsFreezeFailed(String reason, Instant eventTime) {
    if (this.status != OrderStatus.FREEZING_MARGIN) {
      return;
    }
    this.status = OrderStatus.REJECTED;
    this.rejectedReason = reason != null ? reason : "FundsFreezeFailed";
  }

  public void onTradeExecuted(
      BigDecimal fillPrice, BigDecimal fillQuantity, BigDecimal feeDelta, Instant executedAt) {
    BigDecimal previousFilled = this.filledQuantity == null ? BigDecimal.ZERO : this.filledQuantity;
    BigDecimal orderQuantity = this.quantity;

    BigDecimal newFilled = previousFilled.add(fillQuantity);
    if (newFilled.compareTo(orderQuantity) > 0) {
      newFilled = orderQuantity;
    }

    BigDecimal newRemaining = orderQuantity.subtract(newFilled);
    if (newRemaining.compareTo(BigDecimal.ZERO) < 0) {
      newRemaining = BigDecimal.ZERO;
    }

    BigDecimal totalValueBefore =
        this.avgFillPrice == null ? BigDecimal.ZERO : this.avgFillPrice.multiply(previousFilled);
    BigDecimal totalValueAfter = totalValueBefore.add(fillPrice.multiply(fillQuantity));

    BigDecimal newAvgPrice =
        newFilled.compareTo(BigDecimal.ZERO) == 0
            ? this.avgFillPrice
            : totalValueAfter.divide(
                newFilled, ScaleConstant.COMMON_SCALE, java.math.RoundingMode.HALF_UP);

    this.filledQuantity = newFilled;
    this.remainingQuantity = newRemaining;
    this.avgFillPrice = newAvgPrice;
    this.fee = this.fee == null ? feeDelta : this.fee.add(feeDelta);

    boolean orderFilled = newRemaining.compareTo(BigDecimal.ZERO) == 0;
    this.status = orderFilled ? OrderStatus.FILLED : OrderStatus.PARTIALLY_FILLED;

    if (orderFilled && this.filledAt == null) {
      this.filledAt = executedAt;
    }
  }

  public void onPositionIntentDetermined(PositionIntentType intentType) {
    this.intent = intentType;
    if (intentType == PositionIntentType.INCREASE) {
      this.status = OrderStatus.FREEZING_MARGIN;
    } else {
      this.status = OrderStatus.NEW;
      this.submittedAt = Instant.now();
    }
  }

  public void incrementVersion() {
    if (version == null) {
      version = 0;
    }
    this.version = this.version + 1;
  }
}
