package open.vincentf13.api.risk;

import jakarta.validation.constraints.NotBlank;

/**
 * Contract for synchronous risk check requests.
 */
public record RiskCheckRequestDto(@NotBlank String accountId,
                                  @NotBlank String symbol,
                                  String orderType) {
}
