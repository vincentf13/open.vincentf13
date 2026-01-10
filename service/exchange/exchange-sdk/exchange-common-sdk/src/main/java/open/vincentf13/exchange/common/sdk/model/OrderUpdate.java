package open.vincentf13.exchange.common.sdk.model;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import open.vincentf13.exchange.common.sdk.constants.ValidationConstant;
import open.vincentf13.exchange.common.sdk.enums.OrderSide;
import open.vincentf13.sdk.core.validator.Id;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderUpdate {
    
    @NotNull(groups = Id.class)
    private Long orderId;
    private BigDecimal price;
    private OrderSide side;
    @DecimalMin(value = ValidationConstant.Names.NON_NEGATIVE, inclusive = true)
    private BigDecimal remainingQuantity;
    private boolean isTaker;
}
