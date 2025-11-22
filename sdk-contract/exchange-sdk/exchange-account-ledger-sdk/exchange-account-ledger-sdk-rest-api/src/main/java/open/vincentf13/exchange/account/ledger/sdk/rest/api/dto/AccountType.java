package open.vincentf13.exchange.account.ledger.sdk.rest.api.dto;

import java.util.Locale;

public enum AccountType {
    SPOT_MAIN,
    ISOLATED_MARGIN;

    public static AccountType fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("accountType is required");
        }
        try {
            return AccountType.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown account type: " + value, ex);
        }
    }

    public String value() {
        return name();
    }
}
