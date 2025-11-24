package open.vincentf13.exchange.sdk.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum AssetSymbol {
    BTC,
    ETH,
    USDT,
    UNKNOWN;

    @JsonCreator
    public static AssetSymbol fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("asset is required");
        }
        String normalized = value.toUpperCase(Locale.ROOT);
        try {
            return AssetSymbol.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            return UNKNOWN;
        }
    }

    @JsonValue
    public String code() {
        return name();
    }
}
