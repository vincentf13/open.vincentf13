package open.vincentf13.exchange.account.ledger.sdk.rest.api.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum ReferenceType {
    DEPOSIT,
    WITHDRAWAL,
    ORDER;

    @JsonCreator
    public static ReferenceType fromValue(String value) {
        if (value == null) {
            return null;
        }
        return ReferenceType.valueOf(value.toUpperCase(Locale.ROOT));
    }

    @JsonValue
    public String code() {
        return name();
    }
}
