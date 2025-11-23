package open.vincentf13.sdk.spring.cloud.openfeign;

import open.vincentf13.sdk.spring.mvc.OpenApiResponse;

import java.util.Objects;
import java.util.function.Supplier;

public final class OpenApiClientInvoker {


    private OpenApiClientInvoker() {
    }

    public static <T> T call(Supplier<OpenApiResponse<T>> call) {
        Objects.requireNonNull(call, "call");
        OpenApiResponse<T> response = call.get();
        if (response == null) {
            throw new IllegalStateException("response is null");
        }
        if (!response.isSuccess()) {
            String detail = "upstream returned non-success code %s message %s".formatted(response.code(), response.message());
            throw new IllegalStateException(detail);
        }
        T data = response.data();
        if (data == null) {
            throw new IllegalStateException("response data is null");
        }
        return data;
    }
}
