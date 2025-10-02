package open.vincentf13.accountledger.domain;

import java.math.BigDecimal;

/**
 * Immutable value object representing an account balance entry.
 */
public record AccountBalance(String accountId, String asset, BigDecimal balance) {
}
