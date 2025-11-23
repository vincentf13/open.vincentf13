package open.vincentf13.sdk.infra.mysql.retry;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

public final class OpenOptimisticLockRetrier {

    private static final int DEFAULT_MAX_RETRIES = 3;

    private OpenOptimisticLockRetrier() {
    }

    public static <T> T execute(T entity,
                                Consumer<T> mutation,
                                ToIntFunction<T> versionSupplier,
                                BiConsumer<T, Integer> versionSetter,
                                BiFunction<T, Integer, Boolean> updater,
                                Function<T, T> reloader,
                                Supplier<? extends RuntimeException> failureSupplier) {
        Objects.requireNonNull(entity, "entity must not be null");
        Objects.requireNonNull(mutation, "mutation must not be null");
        Objects.requireNonNull(versionSupplier, "versionSupplier must not be null");
        Objects.requireNonNull(versionSetter, "versionSetter must not be null");
        Objects.requireNonNull(updater, "updater must not be null");
        Objects.requireNonNull(reloader, "reloader must not be null");
        Objects.requireNonNull(failureSupplier, "failureSupplier must not be null");

        T current = entity;
        int retries = 0;
        while (retries < DEFAULT_MAX_RETRIES) {
            int currentVersion = versionSupplier.applyAsInt(current);
            mutation.accept(current);
            versionSetter.accept(current, currentVersion + 1);
            if (updater.apply(current, currentVersion)) {
                return current;
            }
            retries++;
            current = reloader.apply(current);
        }
        throw failureSupplier.get();
    }
}
