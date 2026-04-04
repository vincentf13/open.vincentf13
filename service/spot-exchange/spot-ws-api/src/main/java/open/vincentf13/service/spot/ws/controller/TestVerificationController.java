package open.vincentf13.service.spot.ws.controller;

import open.vincentf13.service.spot.infra.chronicle.LongValue;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.model.*;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static open.vincentf13.service.spot.infra.Constants.*;

@RestController
@RequestMapping("/api/test")
public class TestVerificationController {
    private static final String GC_META_SEPARATOR = "\u001F";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    /** CPU 指標配置：消除平行陣列，保證 name/history/current 三者對齊 */
    private record CpuMetric(String name, long historyKey, long currentKey) {}
    private static final List<CpuMetric> CPU_METRICS = List.of(
        new CpuMetric("matching_receiver", MetricsKey.CPU_ID_AERON_RECEIVER, MetricsKey.CPU_ID_CURRENT_AERON_RECEIVER),
        new CpuMetric("gateway_sender",    MetricsKey.CPU_ID_AERON_SENDER,   MetricsKey.CPU_ID_CURRENT_AERON_SENDER),
        new CpuMetric("netty_boss",        MetricsKey.CPU_ID_NETTY_BOSS,     MetricsKey.CPU_ID_CURRENT_NETTY_BOSS),
        new CpuMetric("netty_worker_1",    MetricsKey.CPU_ID_NETTY_WORKER_1, MetricsKey.CPU_ID_CURRENT_NETTY_WORKER_1),
        new CpuMetric("netty_worker_2",    MetricsKey.CPU_ID_NETTY_WORKER_2, MetricsKey.CPU_ID_CURRENT_NETTY_WORKER_2),
        new CpuMetric("netty_worker_3",    MetricsKey.CPU_ID_NETTY_WORKER_3, MetricsKey.CPU_ID_CURRENT_NETTY_WORKER_3),
        new CpuMetric("netty_worker_4",    MetricsKey.CPU_ID_NETTY_WORKER_4, MetricsKey.CPU_ID_CURRENT_NETTY_WORKER_4)
    );

    @GetMapping("/metrics/tps")
    public List<Map<String, Object>> getTpsHistory() {
        TreeMap<Long, Long> sorted = new TreeMap<>();
        Storage.self().tpsHistory().forEach((k, v) -> sorted.put(k, v));

        List<Map<String, Object>> result = new ArrayList<>();
        Long lastTotal = null;
        for (var entry : sorted.descendingMap().entrySet()) {
            if (lastTotal != null) {
                long tps = lastTotal - entry.getValue();
                if (tps >= 0) {
                    result.add(Map.of("time", TIME.format(Instant.ofEpochMilli(entry.getKey())), "tps", tps, "total", entry.getValue(), "type", "order"));
                }
            }
            lastTotal = entry.getValue();
        }
        return result;
    }

    @GetMapping("/metrics/saturation")
    public Map<String, Object> getSaturation() {
        Map<String, Object> m = new LinkedHashMap<>();
        putCounterMetrics(m);
        putJvmMetrics(m);
        m.put("cpu_affinity_history", buildCpuAffinityHistory());
        m.put("current_cpu_id", buildCurrentCpuIds());
        m.put("latency", getCombinedLatency());
        return m;
    }

    private void putCounterMetrics(Map<String, Object> target) {
        target.put("netty_recv", get(MetricsKey.NETTY_RECV_COUNT));
        target.put("wal_write", get(MetricsKey.GATEWAY_WAL_WRITE_COUNT));
        target.put("aeron_send", get(MetricsKey.AERON_SEND_COUNT));
        target.put("aeron_recv", get(MetricsKey.AERON_RECV_COUNT));
        target.put("backpressure", get(MetricsKey.AERON_BACKPRESSURE));
        target.put("order_accepted", get(MetricsKey.ORDER_ACCEPTED_COUNT));
        target.put("order_rejected", get(MetricsKey.ORDER_REJECTED_COUNT));
        target.put("matching_receiver_duty_cycle", get(MetricsKey.MATCHING_AERON_RECEVIER_WORKER_DUTY_CYCLE) + "%");
        target.put("gateway_sender_duty_cycle", get(MetricsKey.GATEWAY_AERON_SENDER_WORKER_DUTY_CYCLE) + "%");
    }

    private void putJvmMetrics(Map<String, Object> target) {
        target.put("matching_jvm", formatJvm(MetricsKey.MATCHING_JVM_USED_MB, MetricsKey.MATCHING_JVM_MAX_MB));
        target.put("matching_gc_pause", buildGcPauseInfo(MetricsKey.MATCHING_GC_COUNT, MetricsKey.MATCHING_GC_HISTORY_START));
        target.put("gateway_jvm", formatJvm(MetricsKey.GATEWAY_JVM_USED_MB, MetricsKey.GATEWAY_JVM_MAX_MB));
        target.put("gateway_gc_pause", buildGcPauseInfo(MetricsKey.GATEWAY_GC_COUNT, MetricsKey.GATEWAY_GC_HISTORY_START));
    }

    private Map<String, List<Integer>> buildCpuAffinityHistory() {
        Map<String, List<Integer>> result = new LinkedHashMap<>();
        for (var cm : CPU_METRICS) result.put(cm.name(), parseCpuMask(get(cm.historyKey())));
        return result;
    }

    private Map<String, Object> buildCurrentCpuIds() {
        Map<String, Object> result = new LinkedHashMap<>();
        for (var cm : CPU_METRICS) result.put(cm.name(), getNullable(cm.currentKey()));
        return result;
    }

    // ========== 工具方法 ==========

    private long get(long key) {
        var v = Storage.self().latestMetrics().get(key);
        return v == null ? 0 : v;
    }

    private Long getNullable(long key) {
        return Storage.self().latestMetrics().get(key);
    }

    private List<Integer> parseCpuMask(long mask) {
        List<Integer> ids = new ArrayList<>();
        for (int i = 0; mask != 0 && i < 64; i++, mask >>>= 1) {
            if ((mask & 1) == 1) ids.add(i);
        }
        return ids;
    }

    private String formatJvm(long usedKey, long maxKey) {
        long u = get(usedKey), m = get(maxKey);
        return u == 0 ? "N/A" : "%dMB (%.1f%%)".formatted(u, (double) u / Math.max(m, 1) * 100);
    }

    private Map<String, Object> buildGcPauseInfo(long countKey, long historyStart) {
        long count = get(countKey);
        if (count == 0) return Map.of("pause_count", 0, "pause_history", List.of());

        List<Map<String, Object>> history = new ArrayList<>();
        for (int i = 0; i < MetricsKey.GC_HISTORY_MAX_KEEP; i++) {
            long eventKey = historyStart + i;
            long encoded = get(eventKey);
            if (encoded == 0) continue;
            String[] gcMeta = parseGcMeta(Storage.self().gcEventHistory().get(eventKey));
            history.add(Map.of(
                "time", TIME.format(Instant.ofEpochMilli(encoded / 1_000_000L)),
                "duration_ms", (encoded % 1_000_000L) / 1000.0d,
                "gc_name", gcMeta[0], "gc_action", gcMeta[1], "gc_cause", gcMeta[2]
            ));
        }
        history.sort((a, b) -> b.get("time").toString().compareTo(a.get("time").toString()));
        return Map.of("pause_count", count, "pause_history", history);
    }

    private String[] parseGcMeta(String raw) {
        if (raw == null || raw.isEmpty()) return new String[]{"", "", ""};
        String[] parts = raw.split(GC_META_SEPARATOR, -1);
        return new String[]{
            parts.length > 0 ? parts[0] : "",
            parts.length > 1 ? parts[1] : "",
            parts.length > 2 ? parts[2] : ""
        };
    }

    private List<Map<String, Object>> getCombinedLatency() {
        TreeMap<Long, Map<String, Map<Long, Long>>> rawData = new TreeMap<>(Collections.reverseOrder());

        Storage.self().latencyHistory().forEach((k, v) -> {
            long metricKey = k / 1_000_000_000_000_000L;
            long rest = k % 1_000_000_000_000_000L;
            long p = rest / 1_000_000_000_000L;
            long t = rest % 1_000_000_000_000L;

            String label = metricKey == MetricsKey.LATENCY_MATCHING ? "matching"
                         : metricKey == MetricsKey.LATENCY_TRANSPORT ? "transport" : null;
            if (label != null) {
                rawData.computeIfAbsent(t, k1 -> new HashMap<>())
                       .computeIfAbsent(label, k2 -> new HashMap<>())
                       .put(p, v);
            }
        });

        List<Map<String, Object>> result = new ArrayList<>();
        for (var entry : rawData.entrySet()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("time", TIME.format(Instant.ofEpochSecond(entry.getKey())));
            addLatencyPercentiles(map, "transport", entry.getValue().get("transport"));
            addLatencyPercentiles(map, "matching", entry.getValue().get("matching"));
            result.add(map);
        }
        return result;
    }

    private void addLatencyPercentiles(Map<String, Object> target, String label, Map<Long, Long> pData) {
        if (pData == null) return;
        Map<String, Object> sorted = new LinkedHashMap<>();
        if (pData.containsKey(50L))  sorted.put("p50",  pData.get(50L) + " ns");
        if (pData.containsKey(99L))  sorted.put("p99",  pData.get(99L) + " ns");
        if (pData.containsKey(100L)) sorted.put("p100", pData.get(100L) + " ns");
        target.put(label, sorted);
    }

    @GetMapping("/balance")
    public Map<String, Object> getBalance(@RequestParam long userId, @RequestParam int assetId) {
        Balance b = Storage.self().balances().getUsing(new BalanceKey(userId, assetId), new Balance());
        return b == null ? Map.of("available", 0, "frozen", 0) : Map.of("available", b.getAvailable(), "frozen", b.getFrozen());
    }

    @GetMapping("/order_by_cid")
    public Order getOrderByCid(@RequestParam long userId, @RequestParam long cid) {
        LongValue orderId = Storage.self().clientOrderIdMap().get(new CidKey(userId, cid));
        return orderId == null ? null : Storage.self().orders().get(orderId);
    }
}
