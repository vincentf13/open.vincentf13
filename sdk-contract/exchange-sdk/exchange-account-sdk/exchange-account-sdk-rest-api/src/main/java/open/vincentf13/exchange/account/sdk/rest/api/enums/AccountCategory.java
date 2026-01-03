package open.vincentf13.exchange.account.sdk.rest.api.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.common.sdk.enums.Direction;

import java.math.BigDecimal;

@Getter
@RequiredArgsConstructor
public enum AccountCategory {
    ASSET(true, true),
    LIABILITY(false, true),
    EQUITY(false, false),
    REVENUE(false, true),
    EXPENSE(true, true);
    
    private final boolean debitPositive;
    private final boolean affectsAvailable;
    
    public static BigDecimal delta(AccountCategory category,
                                   Direction direction,
                                   BigDecimal amount) {
        BigDecimal value = amount.abs();
        boolean positive = (category.debitPositive && direction == Direction.DEBIT)
                           || (!category.debitPositive && direction == Direction.CREDIT);
        return positive ? value : value.negate();
    }
    
    public boolean calculatesAvailableBalance() {
        return affectsAvailable;
    }
}
