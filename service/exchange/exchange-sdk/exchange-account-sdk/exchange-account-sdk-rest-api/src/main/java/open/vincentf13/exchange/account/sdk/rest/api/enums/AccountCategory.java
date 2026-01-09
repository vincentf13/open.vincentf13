package open.vincentf13.exchange.account.sdk.rest.api.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.common.sdk.enums.Direction;

import java.math.BigDecimal;

@Getter
@RequiredArgsConstructor
public enum AccountCategory {
    ASSET(true),
    LIABILITY(false),
    EQUITY(false),
    REVENUE(false),
    EXPENSE(true);
    
    private final boolean debitPositive;
    
    public static BigDecimal delta(AccountCategory category,
                                   Direction direction,
                                   BigDecimal amount) {
        BigDecimal value = amount.abs();
        boolean positive = (category.debitPositive && direction == Direction.DEBIT)
                           || (!category.debitPositive && direction == Direction.CREDIT);
        return positive ? value : value.negate();
    }
    
}
