package open.vincentf13.exchange.risk.sdk.rest.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderPrecheckResponse {
    private boolean allow;
    private BigDecimal requiredMargin;
    private BigDecimal fee;
    private String reason;
}
