//package open.vincentf13.sdk.core.metrics;
//
//import io.micrometer.core.instrument.*;
//import io.micrometer.core.instrument.binder.jvm.*;
//import io.micrometer.core.instrument.binder.kafka.KafkaClientMetrics;
//import io.micrometer.core.instrument.binder.logging.LogbackMetrics;
//import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
//
//import java.util.Objects;
//import java.util.concurrent.Callable;
//import java.util.concurrent.ConcurrentHashMap;
//import java.util.concurrent.ExecutorService;
//import java.util.concurrent.atomic.AtomicLong;
//
//public final class Metrics {
//    private static MeterRegistry REG;
//    private static final ConcurrentHashMap<String, Counter> CACH_COUNTER = new ConcurrentHashMap<>();
//    private static final ConcurrentHashMap<String, Timer> CACH_TIMER = new ConcurrentHashMap<>();
//    private static final ConcurrentHashMap<String, LongTaskTimer> CACH_LTT = new ConcurrentHashMap<>();
//
//    private Metrics() {
//    }
//
//    public static void init(MeterRegistry registry, String app, String env) {
//        REG = Objects.requireNonNull(registry);
//        REG.config().commonTags("app", app, "env", env);
//        new ClassLoaderMetrics().bindTo(REG);
//        new JvmMemoryMetrics().bindTo(REG);
//        new JvmGcMetrics().bindTo(REG);
//        new JvmThreadMetrics().bindTo(REG);
//        new ProcessorMetrics().bindTo(REG);
//        new LogbackMetrics().bindTo(REG);
//    }
//
//    public static void inc(String name, double step, String... tags) {
//        counter(name, tags).increment(step);
//    }
//
//    public static void one(String name, String... tags) {
//        counter(name, tags).increment();
//    }
//
//    public static <T> T time(String name, Callable<T> c, String... tags) throws Exception {
//        Timer.Sample s = Timer.start(REG);
//        try {
//            return c.call();
//        } finally {
//            s.stop(timer(name, tags));
//        }
//    }
//
//    public static void time(String name, Runnable r, String... tags) {
//        Timer.Sample s = Timer.start(REG);
//        try {
//            r.run();
//        } finally {
//            s.stop(timer(name, tags));
//        }
//    }
//
//    public static LongTaskTimer.Sample longTaskStart(String name, String... tags) {
//        return ltt(name, tags).start();
//    }
//
//    public static void longTaskStop(LongTaskTimer.Sample s) {
//        if (s != null) s.stop();
//    }
//
//    public static AtomicLong gauge(String name, AtomicLong ref, String... tags) {
//        Gauge.builder(name, ref, AtomicLong::get).tags(tags).register(REG);
//        return ref;
//    }
//
//    public static void monitorExecutor(ExecutorService ex, String name, String... tags) {
//        ExecutorServiceMetrics.monitor(REG, ex, name, Tags.of(tags));
//    }
//
//    public static void bindKafkaMetrics(org.apache.kafka.clients.producer.Producer<?, ?> p, String clientId) {
//        new KafkaClientMetrics(p).bindTo(REG);
//    }
//
//    public static void bindKafkaMetrics(org.apache.kafka.clients.consumer.Consumer<?, ?> c, String clientId) {
//        new KafkaClientMetrics(c).bindTo(REG);
//    }
//
//    private static Counter counter(String name, String... tags) {
//        String key = key(name, tags);
//        return CACH_COUNTER.computeIfAbsent(key,
//                k -> Counter.builder(name).tags(tags).register(REG));
//    }
//
//    private static Timer timer(String name, String... tags) {
//        String key = key(name, tags);
//        return CACH_TIMER.computeIfAbsent(key,
//                k -> Timer.builder(name)
//                        .tags(tags)
//                        .publishPercentiles(0.5, 0.95, 0.99)
//                        .publishPercentileHistogram()
//                        .register(REG));
//    }
//
//    private static LongTaskTimer ltt(String name, String... tags) {
//        String key = key(name, tags);
//        return CACH_LTT.computeIfAbsent(key,
//                k -> LongTaskTimer.builder(name).tags(tags).register(REG));
//    }
//
//    private static String key(String name, String... tags) {
//        return name + "|" + String.join(",", tags == null ? new String[0] : tags);
//    }
//}
