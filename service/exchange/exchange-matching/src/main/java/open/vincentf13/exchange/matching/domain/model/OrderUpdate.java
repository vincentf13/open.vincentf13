package open.vincentf13.exchange.matching.domain.model;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import open.vincentf13.exchange.common.sdk.constants.ValidationConstant;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderUpdate {
    
    @NotNull
    private Long orderId;
    @DecimalMin(value = ValidationConstant.Names.NON_NEGATIVE, inclusive = true)
    private BigDecimal remainingQuantity;
    private boolean isTaker;
}
