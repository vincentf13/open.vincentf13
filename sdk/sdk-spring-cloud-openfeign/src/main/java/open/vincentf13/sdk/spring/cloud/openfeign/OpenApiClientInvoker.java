package open.vincentf13.sdk.spring.cloud.openfeign;

import open.vincentf13.sdk.core.OpenStackWalker;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;

import java.util.Objects;
import java.util.function.Supplier;

public final class OpenApiClientInvoker {

    private static final int CALLER_SKIP = 4;

    private OpenApiClientInvoker() {
    }

    public static <T> T call(Supplier<OpenApiResponse<T>> call) {
        Objects.requireNonNull(call, "call");
        String operation = OpenStackWalker.resolveOperation(call, CALLER_SKIP);
        OpenApiResponse<T> response = call.get();
        if (response == null) {
            throw new IllegalStateException("operation=%s detail=response is null".formatted(operation));
        }
        if (!response.isSuccess()) {
            String detail = "upstream returned non-success code %s message %s".formatted(response.code(), response.message());
            throw new IllegalStateException("operation=%s detail=%s".formatted(operation, detail));
        }
        T data = response.data();
        if (data == null) {
            throw new IllegalStateException("operation=%s detail=response data is null".formatted(operation));
        }
        return data;
    }
}
