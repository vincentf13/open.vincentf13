package open.vincentf13.exchange.market.sdk.rest.api.enums;

import lombok.Getter;

import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;

@Getter
public enum KlinePeriod {
    ONE_MINUTE("1m", Duration.ofMinutes(1)),
    FIVE_MINUTES("5m", Duration.ofMinutes(5)),
    ONE_HOUR("1h", Duration.ofHours(1)),
    ONE_DAY("1d", Duration.ofDays(1));

    private final String value;
    private final Duration duration;

    KlinePeriod(String value, Duration duration) {
        this.value = value;
        this.duration = duration;
    }

    public static KlinePeriod fromValue(String value) {
        return Arrays.stream(values())
                .filter(p -> Objects.equals(p.value, value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported K-line period: " + value));
    }
}
