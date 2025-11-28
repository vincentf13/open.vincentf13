package open.vincentf13.sdk.spring.cloud.openfeign;

import open.vincentf13.sdk.spring.mvc.OpenApiResponse;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

public final class OpenApiClientInvoker {
    
    private static final Function<String, RuntimeException> DEFAULT_EXCEPTION_FACTORY =
            msg -> new IllegalStateException(msg);
    
    private OpenApiClientInvoker() {
    }
    
    public static <T> T call(Supplier<OpenApiResponse<T>> call) {
        return call(call, null);
    }
    
    public static <T> T call(Supplier<OpenApiResponse<T>> call,
                             Function<String, RuntimeException> exceptionFactory) {
        Objects.requireNonNull(call, "call");
        Function<String, RuntimeException> factory =
                exceptionFactory != null ? exceptionFactory : DEFAULT_EXCEPTION_FACTORY;
        OpenApiResponse<T> response = call.get();
        if (response == null) {
            throw factory.apply("response is null");
        }
        if (!response.isSuccess()) {
            String detail = "upstream returned non-success code %s message %s".formatted(response.code(), response.message());
            throw factory.apply(detail);
        }
        T data = response.data();
        if (data == null) {
            throw factory.apply("response data is null");
        }
        return data;
    }
}
