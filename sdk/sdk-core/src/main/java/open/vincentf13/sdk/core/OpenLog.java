package open.vincentf13.sdk.core;

import org.slf4j.Logger;

import java.util.function.Supplier;

/**
 * private static final Logger log = LoggerFactory.getLogger(OrderService.class);
 *
 * [INFO]
 * info(log, "OrderCreated", "created", "orderId", 12345, "userId", 6789);
 * 輸出
 * [OrderCreated] created | orderId=12345 userId=6789
 *
 * [DEBUG] 未開時，Supplier 不會執行，無輸出
 * debug(log, "CalcPrice", () -> "rule=" + heavyCompute(), "sku", "A100");
 * 輸出：
 * （無，因為 DEBUG 未開）
 *
 * [ERROR] 含例外
 * try {
 * save();
 * } catch (Exception e) {
 * error(log, "OrderSaveFailed", "db error", e, "orderId", 12345);
 * }
 * 輸出（含堆疊）
 * [OrderSaveFailed] db error | orderId=12345
 * java.lang.RuntimeException: save failed
 * at open.vincentf13.service.OrderService.save(OrderService.java:42)
 * at open.vincentf13.service.OrderService.demo(OrderService.java:21)
 * ... 省略
 */
public final class OpenLog {
    private OpenLog() {
    }

    // INFO

    /**
     *  info(log, "OrderCreated", "created", "orderId", 12345, "userId", 6789);
     *
     *  輸出
     *  [OrderCreated] created | orderId=12345 userId=6789
     */
    public static String info(Logger log, String event, String msg, Object... kv) {
        if (!log.isInfoEnabled()) return "Info Not Enabled";
        String finalLog = format(event, msg, kv);
        log.info(finalLog);
        return finalLog;
    }
    // WARN
    public static String warn(Logger log, String event, String msg, Object... kv) {
        if (!log.isWarnEnabled()) return "Warn Not Enabled";
        String finalLog = format(event, msg, kv);
        log.warn(finalLog);
        return finalLog;
    }

    public static String warn(Logger log, String event, String msg, Throwable t, Object... kv) {
        if (!log.isWarnEnabled()) return "Warn Not Enabled";
        String finalLog = format(event, msg, kv);
        log.warn(finalLog, t);
        return finalLog;
    }

    // ERROR
    /**
     *   error(log, "OrderSaveFailed", "db error", e, "orderId", 12345);
     *
     *  輸出（含堆疊）
     *  [OrderSaveFailed] db error | orderId=12345
     *  java.lang.RuntimeException: save failed
     *  at open.vincentf13.service.OrderService.save(OrderService.java:42)
     *  at open.vincentf13.service.OrderService.demo(OrderService.java:21)
     */
    public static String error(Logger log, String event, String msg, Throwable t, Object... kv) {
        if (!log.isErrorEnabled()) return "Error Not Enabled";
        String finalLog = format(event, msg, kv);
        log.error(finalLog, t);
        return finalLog;
    }

    // DEBUG（延遲訊息）
    /**
     *
     * debug(log, "CalcPrice", () -> "rule=" + heavyCompute(), "sku", "A100");
     * 輸出
     * [CalcPrice] rule=xx | sku=A100
     */
    public static String debug(Logger log, String event, Supplier<String> msg, Object... kv) {
        if (!log.isDebugEnabled()) return "Debug Not Enabled";
        String finalLog = format(event, safeGet(msg), kv);
        log.debug(finalLog);
        return finalLog;
    }

    public static String debug(Logger log, String event, Supplier<String> msg, Throwable t, Object... kv) {
        if (!log.isDebugEnabled()) return "Debug Not Enabled";
        String finalLog = format(event, safeGet(msg), kv);
        log.debug(finalLog, t);
        return finalLog;
    }

    // TRACE（延遲訊息）
    public static String trace(Logger log, String event, Supplier<String> msg, Object... kv) {
        if (!log.isTraceEnabled()) return "Trace Not Enabled";
        String finalLog = format(event, safeGet(msg), kv);
        log.trace(finalLog);
        return finalLog;
    }

    // key=value 輕量拼接；不建 Map、不用 streams
    private static String format(String event, String msg, Object... kv) {
        StringBuilder sb = new StringBuilder(64);
        if (event != null && !event.isEmpty()) sb.append('[').append(event).append("] ");
        if (msg != null) sb.append(msg);
        if (kv != null && kv.length > 0) {
            sb.append(" | ");
            for (int i = 0; i < kv.length; i += 2) {
                Object k = kv[i];
                Object v = (i + 1 < kv.length) ? kv[i + 1] : "<missing>";
                if (i > 0) sb.append(' ');
                sb.append(k).append('=').append(v);
            }
        }
        return sb.toString();
    }

    private static String safeGet(Supplier<String> s) {
        try {
            return s == null ? null : s.get();
        } catch (Exception e) {
            return "<msgSupplier-error>";
        }
    }
}
