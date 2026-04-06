package open.vincentf13.service.spot.ws.controller;

import open.vincentf13.service.spot.infra.chronicle.LongValue;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.infra.metrics.StaticMetricsHolder;
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
        new CpuMetric("gateway_wal_sender", MetricsKey.CPU_ID_WAL_SENDER,     MetricsKey.CPU_ID_CURRENT_WAL_SENDER),
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

    @PostMapping("/metrics/reset")
    public Map<String, Object> resetMetrics() {
        Storage s = Storage.self();
        s.latencyHistory().clear();
        s.counterHistory().clear();
        s.tpsHistory().clear();
        s.dutyCycleHistory().clear();
        s.gcEventHistory().clear();
        // 重置累計計數器
        StaticMetricsHolder.values().forEach((k, v) -> v.set(0));
        // 排空 in-memory latency window
        StaticMetricsHolder.snapshotLatencyAndReset();
        return Map.of("status", "ok", "time", TIME.format(java.time.Instant.now()));
    }

    @GetMapping("/metrics/saturation")
    public Map<String, Object> getSaturation() {
        Map<String, Object> m = new LinkedHashMap<>();
        putCounterMetrics(m);
        m.put("counter_history", getCounterHistory());
        m.put("duty_cycle_history", getDutyCycleHistory());
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
        target.put("aeron_send_backpressure", get(MetricsKey.AERON_BACKPRESSURE));
        target.put("aeron_recv", get(MetricsKey.AERON_RECV_COUNT));
        target.put("aeron_dropped", get(MetricsKey.AERON_DROPPED_COUNT));
        target.put("order_accepted", get(MetricsKey.ORDER_ACCEPTED_COUNT));
        target.put("order_rejected", get(MetricsKey.ORDER_REJECTED_COUNT));
        target.put("order_duplicate", get(MetricsKey.ORDER_DUPLICATE_COUNT));
        target.put("report_recv", get(MetricsKey.REPORT_RECV_COUNT));
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

    private static final long COUNTER_KEY_UNIT = 1_000_000_000_000L;
    private record CounterMetric(String name, long key) {}
    private static final List<CounterMetric> COUNTER_METRICS = List.of(
        new CounterMetric("netty_recv", MetricsKey.NETTY_RECV_COUNT),
        new CounterMetric("wal_write", MetricsKey.GATEWAY_WAL_WRITE_COUNT),
        new CounterMetric("aeron_send", MetricsKey.AERON_SEND_COUNT),
        new CounterMetric("aeron_send_backpressure", MetricsKey.AERON_BACKPRESSURE),
        new CounterMetric("aeron_recv", MetricsKey.AERON_RECV_COUNT),
        new CounterMetric("aeron_dropped", MetricsKey.AERON_DROPPED_COUNT),
        new CounterMetric("order_accepted", MetricsKey.ORDER_ACCEPTED_COUNT),
        new CounterMetric("order_rejected", MetricsKey.ORDER_REJECTED_COUNT),
        new CounterMetric("order_duplicate", MetricsKey.ORDER_DUPLICATE_COUNT),
        new CounterMetric("report_recv", MetricsKey.REPORT_RECV_COUNT)
    );

    private List<Map<String, Object>> getCounterHistory() {
        // 將扁平的 (counterKey * UNIT + epochSec) -> total 資料，重新按秒聚合並計算每秒增量
        TreeMap<Long, Map<String, Long>> bySec = new TreeMap<>();
        Storage.self().counterHistory().forEach((k, total) -> {
            long counterKey = k / COUNTER_KEY_UNIT;
            long epochSec = k % COUNTER_KEY_UNIT;
            String label = counterKeyToLabel(counterKey);
            if (label != null) {
                bySec.computeIfAbsent(epochSec, s -> new LinkedHashMap<>()).put(label, total);
            }
        });

        List<Map<String, Object>> result = new ArrayList<>();
        Map<String, Long> prevTotals = null;
        // 升序累積以計算增量，最後反向輸出（最新在前）
        List<Map<String, Object>> ordered = new ArrayList<>();
        for (var entry : bySec.entrySet()) {
            if (prevTotals == null) { prevTotals = entry.getValue(); continue; }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("time", TIME.format(Instant.ofEpochSecond(entry.getKey())));
            Map<String, Long> curr = entry.getValue();
            for (var cm : COUNTER_METRICS) {
                Long now = curr.get(cm.name());
                Long prev = prevTotals.get(cm.name());
                long delta = (now != null && prev != null) ? Math.max(0, now - prev) : 0L;
                row.put(cm.name(), delta);
            }
            ordered.add(row);
            prevTotals = curr;
        }
        for (int i = ordered.size() - 1; i >= 0; i--) result.add(ordered.get(i));
        return result;
    }

    private String counterKeyToLabel(long key) {
        for (var cm : COUNTER_METRICS) if (cm.key() == key) return cm.name();
        return null;
    }

    private List<Map<String, Object>> getDutyCycleHistory() {
        final long DUTY_KEY_UNIT = 1_000_000_000_000L;
        TreeMap<Long, Map<String, Long>> rawData = new TreeMap<>(Collections.reverseOrder());

        Storage.self().dutyCycleHistory().forEach((k, v) -> {
            long dutyKey = k / DUTY_KEY_UNIT;
            long epochSec = k % DUTY_KEY_UNIT;
            String label = dutyKeyToLabel(dutyKey);
            if (label != null) {
                rawData.computeIfAbsent(epochSec, k1 -> new LinkedHashMap<>()).put(label, v);
            }
        });

        List<Map<String, Object>> result = new ArrayList<>();
        for (var entry : rawData.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("time", TIME.format(Instant.ofEpochSecond(entry.getKey())));
            Map<String, Long> workers = entry.getValue();
            putDutyPercent(row, "matching_receiver", workers);
            putDutyPercent(row, "gateway_wal_sender", workers);
            putDutyPercent(row, "gateway_report_receiver", workers);
            result.add(row);
        }
        return result;
    }

    private void putDutyPercent(Map<String, Object> target, String label, Map<String, Long> workers) {
        Long v = workers.get(label);
        target.put(label, v == null ? "0%" : v + "%");
    }

    private String dutyKeyToLabel(long dutyKey) {
        if (dutyKey == MetricsKey.MATCHING_AERON_RECEVIER_WORKER_DUTY_CYCLE) return "matching_receiver";
        if (dutyKey == MetricsKey.GATEWAY_WAL_SENDER_DUTY_CYCLE) return "gateway_wal_sender";
        if (dutyKey == MetricsKey.GATEWAY_REPORT_RECEIVER_DUTY_CYCLE) return "gateway_report_receiver";
        return null;
    }

    private List<Map<String, Object>> getCombinedLatency() {
        TreeMap<Long, Map<String, Map<Long, Long>>> rawData = new TreeMap<>(Collections.reverseOrder());

        Storage.self().latencyHistory().forEach((k, v) -> {
            long metricKey = k / 1_000_000_000_000_000L;
            long rest = k % 1_000_000_000_000_000L;
            long p = rest / 1_000_000_000_000L;
            long t = rest % 1_000_000_000_000L;

            String label = switch ((int) metricKey) {
                case (int) MetricsKey.LATENCY_MATCHING -> "matching";
                case (int) MetricsKey.LATENCY_TRANSPORT -> "transport";
                case (int) MetricsKey.LATENCY_REPORT_DELIVERY -> "report_delivery";
                case (int) MetricsKey.LATENCY_NETTY_PROCESS -> "netty_process";
                case (int) MetricsKey.LATENCY_DISRUPTOR_WAIT -> "disruptor_wait";
                case (int) MetricsKey.LATENCY_SENDER_ENCODE -> "sender_encode";
                case (int) MetricsKey.LATENCY_CONTROL_POLL -> "control_poll";
                case (int) MetricsKey.LATENCY_GATEWAY_TOTAL -> "gateway_total";
                default -> null;
            };
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
            addLatencyPercentiles(map, "report_delivery", entry.getValue().get("report_delivery"));
            addLatencyPercentiles(map, "netty_process", entry.getValue().get("netty_process"));
            addLatencyPercentiles(map, "disruptor_wait", entry.getValue().get("disruptor_wait"));
            addLatencyPercentiles(map, "sender_encode", entry.getValue().get("sender_encode"));
            addLatencyPercentiles(map, "control_poll", entry.getValue().get("control_poll"));
            addLatencyPercentiles(map, "gateway_total", entry.getValue().get("gateway_total"));
            result.add(map);
        }
        return result;
    }

    private void addLatencyPercentiles(Map<String, Object> target, String label, Map<Long, Long> pData) {
        if (pData == null) return;
        Map<String, Object> sorted = new LinkedHashMap<>();
        if (pData.containsKey(50L))  sorted.put("p50",  pData.get(50L) + " ns");
        if (pData.containsKey(90L))  sorted.put("p90",  pData.get(90L) + " ns");
        if (pData.containsKey(99L))  sorted.put("p99",  pData.get(99L) + " ns");
        if (pData.containsKey(999L)) sorted.put("p999", pData.get(999L) + " ns");
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
