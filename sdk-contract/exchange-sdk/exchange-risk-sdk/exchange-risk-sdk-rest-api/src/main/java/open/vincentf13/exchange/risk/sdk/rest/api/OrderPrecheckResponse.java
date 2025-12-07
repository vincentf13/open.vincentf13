package open.vincentf13.exchange.risk.sdk.rest.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderPrecheckResponse {
    private boolean pass;
    private String message;
}
