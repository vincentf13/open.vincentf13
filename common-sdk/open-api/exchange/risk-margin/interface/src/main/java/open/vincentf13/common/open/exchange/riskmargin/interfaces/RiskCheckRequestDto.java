package open.vincentf13.common.open.exchange.riskmargin.interfaces;

import jakarta.validation.constraints.NotBlank;

/**
 * Contract for synchronous risk check requests.
 */
public record RiskCheckRequestDto(@NotBlank String accountId,
                                  @NotBlank String symbol,
                                  String orderType) {
}
