package open.vincentf13.sdk.core;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

public final class OpenConstant {

    private OpenConstant() {
    }

    public static final String BASE_PACKAGE = "open.vincentf13";
    public static final String TEST_BASE_PACKAGE = "test." + BASE_PACKAGE;

    @Getter
    @Accessors(fluent = true)
    @RequiredArgsConstructor
    public enum Package {
        BASE(BASE_PACKAGE),
        TEST(TEST_BASE_PACKAGE);

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
