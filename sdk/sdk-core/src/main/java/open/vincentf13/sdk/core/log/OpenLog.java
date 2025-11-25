package open.vincentf13.sdk.core.log;

import open.vincentf13.sdk.core.OpenStackWalker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.lang.StackWalker;

/*
 * 統一日誌輸出，事件需實作 OpenEvent。
 * 範例（事件來自各模組自定義的 XxxEventEnum）：
 *   OpenLog.info(SomeEventEnum.ORDER_CREATED, "orderId", 12345);
 *   OpenLog.warn(SomeEventEnum.ORDER_SAVE_FAILED, ex, "orderId", 12345);
 *   OpenLog.debug(SomeEventEnum.CALC_PRICE, "sku", "A100");
 *
 * 亦保留需手動傳入 Logger 的 overload，便於特殊情境控制。
 */
public final class OpenLog {

    private static final Map<String, Logger> LOGGER_CACHE = new ConcurrentHashMap<>();

    private OpenLog() {
    }

    // ===== 無需傳入 Logger，會自動解析呼叫端類別並附上 op=caller =====

    public static String info(OpenEvent event, Object... kv) {
        Logger log = resolveLogger();
        if (!log.isInfoEnabled()) return "Info Not Enabled";
        String finalLog = format(event, null, kv);
        log.info(finalLog);
        return finalLog;
    }

    public static String warn(OpenEvent event, Object... kv) {
        Logger log = resolveLogger();
        if (!log.isWarnEnabled()) return "Warn Not Enabled";
        String finalLog = format(event, null, kv);
        log.warn(finalLog);
        return finalLog;
    }

    public static String warn(OpenEvent event, Throwable t, Object... kv) {
        Logger log = resolveLogger();
        if (!log.isWarnEnabled()) return "Warn Not Enabled";
        String finalLog = format(event, null, kv);
        log.warn(finalLog, t);
        return finalLog;
    }

    public static String error(OpenEvent event, Throwable t, Object... kv) {
        Logger log = resolveLogger();
        if (!log.isErrorEnabled()) return "Error Not Enabled";
        String finalLog = format(event, null, kv);
        log.error(finalLog, t);
        return finalLog;
    }

    public static String debug(OpenEvent event, Object... kv) {
        Logger log = resolveLogger();
        if (!log.isDebugEnabled()) return "Debug Not Enabled";
        String finalLog = format(event, null, kv);
        log.debug(finalLog);
        return finalLog;
    }

    public static String debug(OpenEvent event, Throwable t, Object... kv) {
        Logger log = resolveLogger();
        if (!log.isDebugEnabled()) return "Debug Not Enabled";
        String finalLog = format(event, null, kv);
        log.debug(finalLog, t);
        return finalLog;
    }

    public static String trace(OpenEvent event, Object... kv) {
        Logger log = resolveLogger();
        if (!log.isTraceEnabled()) return "Trace Not Enabled";
        String finalLog = format(event, null, kv);
        log.trace(finalLog);
        return finalLog;
    }

    // ===== 保留需傳入 Logger 的 overload =====

    public static String info(Logger log, OpenEvent event, Object... kv) {
        if (!log.isInfoEnabled()) return "Info Not Enabled";
        String finalLog = format(event, null, kv);
        log.info(finalLog);
        return finalLog;
    }

    public static String warn(Logger log, OpenEvent event, Object... kv) {
        if (!log.isWarnEnabled()) return "Warn Not Enabled";
        String finalLog = format(event, null, kv);
        log.warn(finalLog);
        return finalLog;
    }

    public static String warn(Logger log, OpenEvent event, Throwable t, Object... kv) {
        if (!log.isWarnEnabled()) return "Warn Not Enabled";
        String finalLog = format(event, null, kv);
        log.warn(finalLog, t);
        return finalLog;
    }

    public static String error(Logger log, OpenEvent event, Throwable t, Object... kv) {
        if (!log.isErrorEnabled()) return "Error Not Enabled";
        String finalLog = format(event, null, kv);
        log.error(finalLog, t);
        return finalLog;
    }

    public static String debug(Logger log, OpenEvent event, Object... kv) {
        if (!log.isDebugEnabled()) return "Debug Not Enabled";
        String finalLog = format(event, null, kv);
        log.debug(finalLog);
        return finalLog;
    }

    public static String debug(Logger log, OpenEvent event, Throwable t, Object... kv) {
        if (!log.isDebugEnabled()) return "Debug Not Enabled";
        String finalLog = format(event, null, kv);
        log.debug(finalLog, t);
        return finalLog;
    }

    public static String trace(Logger log, OpenEvent event, Object... kv) {
        if (!log.isTraceEnabled()) return "Trace Not Enabled";
        String finalLog = format(event, null, kv);
        log.trace(finalLog);
        return finalLog;
    }

    // ===== Helper =====

    private static String format(OpenEvent event, String operation, Object... kv) {
        StringBuilder sb = new StringBuilder(64);
        if (event != null && event.event() != null && !event.event().isEmpty()) {
            sb.append('[').append(event.event()).append("] ");
        }
        String msg = event != null ? event.message() : null;
        if (msg != null) {
            sb.append(msg);
        }
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

    private static Logger resolveLogger() {
        Class<?> callerClass = resolveCallerClass();
        String loggerName = callerClass != null ? callerClass.getName() : OpenLog.class.getName();
        return LOGGER_CACHE.computeIfAbsent(loggerName, LoggerFactory::getLogger);
    }

    private static Class<?> resolveCallerClass() {
        return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                .walk(frames -> frames.skip(3).findFirst().map(StackWalker.StackFrame::getDeclaringClass).orElse(null));
    }

    private static String resolveOperation() {
        // 跳過 OpenLog 的封裝層
        return OpenStackWalker.resolveOperation(3);
    }
}
