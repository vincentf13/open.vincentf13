package open.vincentf13.exchange.risk.sdk.rest.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import open.vincentf13.exchange.common.sdk.constants.ValidationConstant;
import open.vincentf13.exchange.common.sdk.enums.OrderSide;
import open.vincentf13.exchange.common.sdk.enums.OrderType;
import open.vincentf13.exchange.common.sdk.enums.PositionIntentType;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderPrecheckRequest {

    @NotNull
    private Long userId;

    @NotNull
    private Long instrumentId;

    @NotNull
    private OrderSide side;

    @NotNull
    private OrderType type;

    @DecimalMin(value = ValidationConstant.Names.PRICE_MIN)
    private BigDecimal price;

    @NotNull
    @DecimalMin(value = ValidationConstant.Names.QUANTITY_MIN)
    private BigDecimal quantity;

    @NotNull
    private PositionIntentType intent;

    @NotNull
    @Valid
    private PositionSnapshot positionSnapshot;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PositionSnapshot {
        private Integer leverage;
        private BigDecimal margin;
        private BigDecimal quantity;
        private BigDecimal markPrice;
        private BigDecimal unrealizedPnl;
    }
}
