package open.vincentf13.sdk.core.log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.StackWalker;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/*
  統一日誌輸出，事件需實作 OpenEvent。
  範例（事件來自各模組自定義的 XxxEvent）：
  OpenLog.info(SomeEvent.ORDER_CREATED, "orderId", 12345);
  OpenLog.warn(SomeEvent.ORDER_SAVE_FAILED, ex, "orderId", 12345);
  OpenLog.debug(SomeEvent.CALC_PRICE, "sku", "A100");
 */
public final class OpenLog {

    private static final Map<String, Logger> LOGGER_CACHE = new ConcurrentHashMap<>();

    private OpenLog() {
    }

    public static String info(OpenEvent event, Object... kv) {
        Logger log = resolveLogger();
        if (!log.isInfoEnabled()) return "Info Not Enabled";
        String finalLog = format(event, kv);
        log.info(finalLog);
        return finalLog;
    }

    public static String warn(OpenEvent event, Object... kv) {
        Logger log = resolveLogger();
        if (!log.isWarnEnabled()) return "Warn Not Enabled";
        String finalLog = format(event, kv);
        log.warn(finalLog);
        return finalLog;
    }

    public static String warn(OpenEvent event, Throwable t, Object... kv) {
        Logger log = resolveLogger();
        if (!log.isWarnEnabled()) return "Warn Not Enabled";
        String finalLog = format(event, kv);
        log.warn(finalLog, t);
        return finalLog;
    }

    public static String error(OpenEvent event, Throwable t, Object... kv) {
        Logger log = resolveLogger();
        if (!log.isErrorEnabled()) return "Error Not Enabled";
        String finalLog = format(event, kv);
        log.error(finalLog, t);
        return finalLog;
    }

    public static String debug(OpenEvent event, Object... kv) {
        Logger log = resolveLogger();
        if (!log.isDebugEnabled()) return "Debug Not Enabled";
        String finalLog = format(event, kv);
        log.debug(finalLog);
        return finalLog;
    }

    public static String debug(OpenEvent event, Throwable t, Object... kv) {
        Logger log = resolveLogger();
        if (!log.isDebugEnabled()) return "Debug Not Enabled";
        String finalLog = format(event, kv);
        log.debug(finalLog, t);
        return finalLog;
    }

    public static String trace(OpenEvent event, Object... kv) {
        Logger log = resolveLogger();
        if (!log.isTraceEnabled()) return "Trace Not Enabled";
        String finalLog = format(event, kv);
        log.trace(finalLog);
        return finalLog;
    }

    private static String format(OpenEvent event, Object... kv) {
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

    public static OpenEvent event(String event) {
        return event(event, null);
    }

    public static OpenEvent event(String event, String message) {
        return new SimpleEvent(event, message);
    }

    private record SimpleEvent(String event, String message) implements OpenEvent {}
}
