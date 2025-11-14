package open.vincentf13.sdk.core;

import java.lang.StackWalker;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Utility helpers around {@link StackWalker} to surface caller information without exposing JDK specifics.
 */
public final class OpenStackWalker {

    private static final StackWalker WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    private OpenStackWalker() {
    }

    /**
     * Resolve the stack frame located after skipping the provided number of frames.
     *
     * @param framesToSkip number of frames to drop starting from the current method (negative values treated as zero)
     * @return formatted caller as "fullyQualifiedClass.method" or "unknown" when unavailable
     */
    public static final String UNKNOWN_OPERATION = "unknown";

    public static String resolveOperation(int framesToSkip) {
        int skip = Math.max(framesToSkip, 0);
        return WALKER.walk(frames -> frames
                .skip(skip)
                .findFirst()
                .map(OpenStackWalker::formatFrame)
                .orElse(UNKNOWN_OPERATION));
    }

    /**
     * Resolve the immediate caller of the invoking method.
     */
    public static String resolveOperation() {
        return resolveOperation(1);
    }

    /**
     * Attempt to resolve the implementation class/method referenced by a lambda expression.
     *
     * @param functional lambda/functional reference
     * @return formatted "class.method" or {@value #UNKNOWN}
     */
    public static String resolveLambdaOperation(Object functional) {
        SerializedLambda lambda = extractSerializedLambda(functional);
        if (lambda == null) {
            return UNKNOWN_OPERATION;
        }
        return lambda.getImplClass().replace('/', '.') + "." + lambda.getImplMethodName();
    }

    /**
     * Resolve operation name by preferring the implementation referenced by the provided Supplier (lambda/method reference).
     * Falls back to stack walking when lambda inspection is not possible.
     */
    public static String resolveOperation(Supplier<?> supplier, int fallbackSkip) {
        String lambdaOp = resolveLambdaOperation(supplier);
        if (!UNKNOWN_OPERATION.equals(lambdaOp)) {
            return lambdaOp;
        }
        return resolveOperation(fallbackSkip);
    }

    private static SerializedLambda extractSerializedLambda(Object candidate) {
        if (candidate == null) {
            return null;
        }
        try {
            Method writeReplace = candidate.getClass().getDeclaredMethod("writeReplace");
            writeReplace.setAccessible(true);
            Object replacement = writeReplace.invoke(candidate);
            if (replacement instanceof SerializedLambda lambda) {
                return lambda;
            }
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
            // ignore - not a lambda or inaccessible
        }
        return null;
    }

    private static String formatFrame(StackWalker.StackFrame frame) {
        Objects.requireNonNull(frame, "frame");
        return frame.getClassName() + "." + frame.getMethodName();
    }
}
