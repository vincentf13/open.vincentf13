package open.vincentf13.common.open.exchange.accountledger.interfaces;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO representing an account balance snapshot shared between services.
 */
public record AccountBalanceDto(@NotBlank String accountId,
                                @NotBlank String asset,
                                @NotBlank String balance) {
}
