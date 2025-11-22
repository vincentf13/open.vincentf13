package open.vincentf13.exchange.account.ledger.sdk.rest.api.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum PlatformAccountCode {
    HOT_WALLET("Hot Wallet"),
    COLD_WALLET("Cold Wallet"),
    EXCHANGE_LIAB("Exchange Liability"),
    INSURANCE_FUND("Insurance Fund"),
    FEE_REVENUE("Fee Revenue"),
    FUNDING_CLEARING("Funding Clearing"),
    PNL_CLEARING("PnL Clearing"),
    TREASURY("Treasury"),
    USER_DEPOSIT("User Deposit");

    private final String displayName;

    PlatformAccountCode(String displayName) {
        this.displayName = displayName;
    }

    @JsonValue
    public String code() {
        return name();
    }

    public String displayName() {
        return displayName;
    }

    @JsonCreator
    public static PlatformAccountCode fromValue(String value) {
        if (value == null) {
            return null;
        }
        return PlatformAccountCode.valueOf(value);
    }
}
