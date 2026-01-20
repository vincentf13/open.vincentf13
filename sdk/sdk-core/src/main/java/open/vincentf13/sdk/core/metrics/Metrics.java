 package open.vincentf13.sdk.core.metrics;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.binder.jvm.*;
import io.micrometer.core.instrument.binder.logging.LogbackMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;

public final class Metrics {
    private static MeterRegistry REG;
    private static final ConcurrentHashMap<String, Counter> CACH_COUNTER = new
 ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Timer> CACH_TIMER = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, LongTaskTimer> CACH_LTT = new
 ConcurrentHashMap<>();

    private Metrics() {
    }

    public static void init(MeterRegistry registry, String app, String env) {
        // 驗證並將傳入的 MeterRegistry 賦值給全域靜態變數 REG，若為 null 則拋出 NullPointerException
        REG = Objects.requireNonNull(registry);
        // 設定所有指標的通用標籤 (Common Tags)，這裡會自動為每個指標附帶 app (應用程式名稱) 和 env (環境) 標籤
        REG.config().commonTags("app", app, "env", env);
        // 綁定 ClassLoader 相關指標 (如載入的類別數量、卸載數量) 到 MeterRegistry
        new ClassLoaderMetrics().bindTo(REG);
        // 綁定 JVM 記憶體相關指標 (如 Heap/Non-Heap 使用量、各代記憶體區塊大小) 到 MeterRegistry
        new JvmMemoryMetrics().bindTo(REG);
        // 綁定 JVM 垃圾回收 (GC) 相關指標 (如 GC 次數、GC 暫停時間、記憶體分配速率) 到 MeterRegistry
        new JvmGcMetrics().bindTo(REG);
        // 綁定 JVM 執行緒相關指標 (如當前執行緒數量、守護執行緒數量、峰值執行緒數) 到 MeterRegistry
        new JvmThreadMetrics().bindTo(REG);
        // 綁定系統處理器相關指標 (如 CPU 核心數、CPU 使用率、系統負載) 到 MeterRegistry
        new ProcessorMetrics().bindTo(REG);
        // 綁定 Logback 日誌相關指標 (如各級別日誌的發生次數 error/warn/info 等) 到 MeterRegistry
        new LogbackMetrics().bindTo(REG);
    }

    public static void inc(String name, double step, String... tags) {
        counter(name, tags).increment(step);
    }

    public static void one(String name, String... tags) {
        counter(name, tags).increment();
    }

    public static <T> T time(String name, Callable<T> c, String... tags) throws Exception {
        Timer.Sample s = Timer.start(REG);
        try {
            return c.call();
        } finally {
            s.stop(timer(name, tags));
        }
    }

    public static void time(String name, Runnable r, String... tags) {
        Timer.Sample s = Timer.start(REG);
        try {
            r.run();
        } finally {
            s.stop(timer(name, tags));
        }
    }

    public static LongTaskTimer.Sample longTaskStart(String name, String... tags) {
        return ltt(name, tags).start();
    }

    public static void longTaskStop(LongTaskTimer.Sample s) {
        if (s != null) s.stop();
    }

    public static AtomicLong gauge(String name, AtomicLong ref, String... tags) {
        Gauge.builder(name, ref, AtomicLong::get).tags(tags).register(REG);
        return ref;
    }

    public static void monitorExecutor(ExecutorService ex, String name, String... tags) {
        ExecutorServiceMetrics.monitor(REG, ex, name, Tags.of(tags));
    }

 //   public static void bindKafkaMetrics(org.apache.kafka.clients.producer.Producer<?, ?> p, String
 //clientId) {
 //       new KafkaClientMetrics(p).bindTo(REG);
 //   }
 //
 //   public static void bindKafkaMetrics(org.apache.kafka.clients.consumer.Consumer<?, ?> c, String
 //clientId) {
 //       new KafkaClientMetrics(c).bindTo(REG);
 //   }

    private static Counter counter(String name, String... tags) {
        String key = key(name, tags);
        return CACH_COUNTER.computeIfAbsent(key,
                k -> Counter.builder(name).tags(tags).register(REG));
    }

    private static Timer timer(String name, String... tags) {
        String key = key(name, tags);
        return CACH_TIMER.computeIfAbsent(key,
                k -> Timer.builder(name)
                        .tags(tags)
                        .publishPercentiles(0.5, 0.95, 0.99)
                        .publishPercentileHistogram()
                        .register(REG));
    }

    private static LongTaskTimer ltt(String name, String... tags) {
        String key = key(name, tags);
        return CACH_LTT.computeIfAbsent(key,
                k -> LongTaskTimer.builder(name).tags(tags).register(REG));
    }

    private static String key(String name, String... tags) {
        return name + "|" + String.join(",", tags == null ? new String[0] : tags);
    }
 }
