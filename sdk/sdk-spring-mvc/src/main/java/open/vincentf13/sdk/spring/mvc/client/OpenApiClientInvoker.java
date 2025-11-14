package open.vincentf13.sdk.spring.mvc.client;

import open.vincentf13.sdk.core.OpenStackWalker;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

public final class OpenApiClientInvoker {

    private static final Function<ErrorMsg, RuntimeException> DEFAULT_EXCEPTION_FACTORY =
            ctx -> new IllegalStateException(ctx.describe());

    private OpenApiClientInvoker() {
    }

    public static <T> T unwrap(Supplier<OpenApiResponse<T>> call) {
        return unwrap(call, DEFAULT_EXCEPTION_FACTORY);
    }

    public static <T> T unwrap(Supplier<OpenApiResponse<T>> call,
                               Function<ErrorMsg, RuntimeException> exceptionFactory) {
        Objects.requireNonNull(call, "call");
        Objects.requireNonNull(exceptionFactory, "exceptionFactory");

        OpenApiResponse<T> response = call.get();
        if (response == null) {
            throw exceptionFactory.apply(new ErrorMsg(call, null, "response is null"));
        }
        if (!response.isSuccess()) {
            String detail = "upstream returned non-success code %s message %s".formatted(response.code(), response.message());
            throw exceptionFactory.apply(new ErrorMsg(call, response, detail));
        }
        T data = response.data();
        if (data == null) {
            throw exceptionFactory.apply(new ErrorMsg(call, response, "response data is null"));
        }
        return data;
    }

    public record ErrorMsg(Supplier<?> call, OpenApiResponse<?> response, String detail) {
        private static final int CALLER_SKIP = 4;

        public String describe() {
            if (response == null) {
                return "operation=%s detail=%s".formatted( OpenStackWalker.resolveOperation(call, CALLER_SKIP), detail);
            }
            return "operation=%s detail=%s responseCode=%s responseMessage=%s".formatted(
                     OpenStackWalker.resolveOperation(call, CALLER_SKIP), detail, response.code(), response.message()
            );
        }
    }
}
