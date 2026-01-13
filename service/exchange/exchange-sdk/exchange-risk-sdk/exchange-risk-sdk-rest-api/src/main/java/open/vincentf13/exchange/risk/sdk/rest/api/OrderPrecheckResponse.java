package open.vincentf13.exchange.risk.sdk.rest.api;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderPrecheckResponse {
  private boolean allow;
  private BigDecimal requiredMargin;
  private BigDecimal fee;
  private String reason;
}
