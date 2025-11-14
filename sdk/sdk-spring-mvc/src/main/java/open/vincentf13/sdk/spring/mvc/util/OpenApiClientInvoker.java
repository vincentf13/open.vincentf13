package open.vincentf13.sdk.spring.mvc.util;

import open.vincentf13.sdk.spring.mvc.OpenApiResponse;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

public final class OpenApiClientInvoker {

    private static final Function<ClientErrorContext, RuntimeException> DEFAULT_EXCEPTION_FACTORY =
            ctx -> new IllegalStateException(ctx.describe());

    private OpenApiClientInvoker() {
    }

    public static <T> T unwrap(Supplier<OpenApiResponse<T>> call, String operation) {
        return unwrap(call, operation, DEFAULT_EXCEPTION_FACTORY);
    }

    public static <T> T unwrap(Supplier<OpenApiResponse<T>> call,
                               String operation,
                               Function<ClientErrorContext, RuntimeException> exceptionFactory) {
        Objects.requireNonNull(call, "call");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(exceptionFactory, "exceptionFactory");

        OpenApiResponse<T> response = call.get();
        if (response == null) {
            throw exceptionFactory.apply(new ClientErrorContext(operation, null, "response is null"));
        }
        if (!response.isSuccess()) {
            String detail = "upstream returned non-success code %s message %s".formatted(response.code(), response.message());
            throw exceptionFactory.apply(new ClientErrorContext(operation, response, detail));
        }
        T data = response.data();
        if (data == null) {
            throw exceptionFactory.apply(new ClientErrorContext(operation, response, "response data is null"));
        }
        return data;
    }

    public static <T, R> R invoke(Supplier<OpenApiResponse<T>> call,
                                  Function<T, R> mapper,
                                  String operation,
                                  Function<ClientErrorContext, RuntimeException> exceptionFactory) {
        Objects.requireNonNull(mapper, "mapper");
        T data = unwrap(call, operation, exceptionFactory);
        return mapper.apply(data);
    }

    public record ClientErrorContext(String operation, OpenApiResponse<?> response, String detail) {
        public String describe() {
            if (response == null) {
                return "operation=%s detail=%s".formatted(operation, detail);
            }
            return "operation=%s detail=%s responseCode=%s responseMessage=%s".formatted(
                    operation, detail, response.code(), response.message()
            );
        }
    }
}
