package open.vincentf13.sdk.core;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

public final class OpenConstant {

    private OpenConstant() {
    }

    @Getter
    @Accessors(fluent = true)
    @RequiredArgsConstructor
    public enum Package {
        BASE("open.vincentf13"),
        TEST("test." + BASE.value);

        private final String value;
    }

    @Getter
    @Accessors(fluent = true)
    @RequiredArgsConstructor
    public enum Header {
        TRACE_ID("X-Trace-Id"),
        REQUEST_ID("X-Request-Id"),
        API_KEY("X-API-KEY");

        private final String value;
    }

    @Getter
    @Accessors(fluent = true)
    @RequiredArgsConstructor
    public enum Auth {
        BEARER_PREFIX("Bearer ");

        private final String value;
    }
}
