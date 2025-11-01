package open.vincentf13.sdk.core;

public final class OpenConstant {

    private OpenConstant() {
    }

    public static final String BASE_PACKAGE = "open.vincentf13";

    public static final String TEST_BASE_PACKAGE = "test." + BASE_PACKAGE;

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    public static final String TRACE_ID_KEY = "traceId";
    public static final String REQUEST_ID_KEY = "requestId";

    public static final String BEARER_PREFIX = "Bearer ";
    public static final String API_KEY_HEADER = "X-API-KEY";
}
