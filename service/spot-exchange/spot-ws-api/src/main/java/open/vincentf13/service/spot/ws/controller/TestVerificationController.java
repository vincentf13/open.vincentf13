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

    @GetMapping("/metrics/tps")
    public List<Map<String, Object>> getTpsHistory() {
        TreeMap<Long, Long> sorted = new TreeMap<>();
        Storage.self().tpsHistory().forEach((k, v) -> sorted.put(k, v));
        
        List<Map<String, Object>> result = new ArrayList<>();
        Long lastTotal = null;
        for (var entry : sorted.entrySet()) {
            if (lastTotal != null) {
                long tps = entry.getValue() - lastTotal;
                if (tps >= 0) {
                    result.add(Map.of("time", TIME.format(Instant.ofEpochMilli(entry.getKey())), "tps", tps, "total", entry.getValue(), "type", "order"));
                }
            }
            lastTotal = entry.getValue();
        }
        Collections.reverse(result);
        return result;
    }

    @GetMapping("/metrics/saturation")
    public Map<String, Object> getSaturation() {
        Map<String, Object> m = new LinkedHashMap<>();
        
        m.put("netty_recv", get(MetricsKey.NETTY_RECV_COUNT));
        m.put("wal_write", get(MetricsKey.GATEWAY_WAL_WRITE_COUNT));
        m.put("aeron_send", get(MetricsKey.AERON_SEND_COUNT));
        m.put("backpressure", get(MetricsKey.AERON_BACKPRESSURE));
        m.put("order_accepted", get(MetricsKey.ORDER_ACCEPTED_COUNT));
        m.put("order_rejected", get(MetricsKey.ORDER_REJECTED_COUNT));
        m.put("matching_receiver_duty_cycle", get(MetricsKey.MATCHING_AERON_RECEVIER_WORKER_DUTY_CYCLE));
        m.put("gateway_sender_duty_cycle", get(MetricsKey.GATEWAY_AERON_SENDER_WORKER_DUTY_CYCLE));

        m.put("matching_jvm", formatJvm(MetricsKey.MATCHING_JVM_USED_MB, MetricsKey.MATCHING_JVM_MAX_MB));
        m.put("matching_gc_pause", buildGcPauseInfo(MetricsKey.MATCHING_GC_COUNT, MetricsKey.MATCHING_GC_HISTORY_START));
        m.put("gateway_jvm", formatJvm(MetricsKey.GATEWAY_JVM_USED_MB, MetricsKey.GATEWAY_JVM_MAX_MB));
        m.put("gateway_gc_pause",  buildGcPauseInfo(MetricsKey.GATEWAY_GC_COUNT,  MetricsKey.GATEWAY_GC_HISTORY_START));

        Map<String, List<Integer>> cpuHistory = new LinkedHashMap<>();
        cpuHistory.put("matching_receiver", parseCpuMask(get(MetricsKey.CPU_ID_AERON_RECEIVER)));
        cpuHistory.put("gateway_sender",    parseCpuMask(get(MetricsKey.CPU_ID_AERON_SENDER)));
        cpuHistory.put("netty_boss",        parseCpuMask(get(MetricsKey.CPU_ID_NETTY_BOSS)));
        cpuHistory.put("netty_worker_1",    parseCpuMask(get(MetricsKey.CPU_ID_NETTY_WORKER_1)));
        cpuHistory.put("netty_worker_2",    parseCpuMask(get(MetricsKey.CPU_ID_NETTY_WORKER_2)));
        cpuHistory.put("netty_worker_3",    parseCpuMask(get(MetricsKey.CPU_ID_NETTY_WORKER_3)));
        cpuHistory.put("netty_worker_4",    parseCpuMask(get(MetricsKey.CPU_ID_NETTY_WORKER_4)));
        m.put("cpu_affinity_history", cpuHistory);

        Map<String, Object> currentCpuIds = new LinkedHashMap<>();
        currentCpuIds.put("matching_receiver", getNullable(MetricsKey.CPU_ID_CURRENT_AERON_RECEIVER));
        currentCpuIds.put("gateway_sender",    getNullable(MetricsKey.CPU_ID_CURRENT_AERON_SENDER));
        currentCpuIds.put("netty_boss",        getNullable(MetricsKey.CPU_ID_CURRENT_NETTY_BOSS));
        currentCpuIds.put("netty_worker_1",    getNullable(MetricsKey.CPU_ID_CURRENT_NETTY_WORKER_1));
        currentCpuIds.put("netty_worker_2",    getNullable(MetricsKey.CPU_ID_CURRENT_NETTY_WORKER_2));
        currentCpuIds.put("netty_worker_3",    getNullable(MetricsKey.CPU_ID_CURRENT_NETTY_WORKER_3));
        currentCpuIds.put("netty_worker_4",    getNullable(MetricsKey.CPU_ID_CURRENT_NETTY_WORKER_4));
        m.put("current_cpu_id", currentCpuIds);
        
        m.put("latency", getCombinedLatency());

        return m;
    }

    private long get(long key) {
        var v = Storage.self().latestMetrics().get(key);
        return v == null ? 0 : v;
    }

    private Long getNullable(long key) {
        return Storage.self().latestMetrics().get(key);
    }

    private List<Integer> parseCpuMask(long mask) {
        List<Integer> ids = new ArrayList<>();
        if (mask == 0) return ids;
        for (int i = 0; i < 64; i++) {
            if (((mask >> i) & 1L) == 1L) ids.add(i);
        }
        return ids;
    }

    private String formatJvm(long usedKey, long maxKey) {
        long u = get(usedKey), m = get(maxKey);
        return u == 0 ? "N/A" : String.format("%dMB (%.1f%%)", u, (double) u / (m == 0 ? 1 : m) * 100);
    }

    private Map<String, Object> buildGcPauseInfo(long countKey, long historyStart) {
        long count = get(countKey);
        if (count == 0) return Map.of("pause_count", 0, "pause_history", List.of(), "semantic", "jvm_gc_notification_pause");

        List<Map<String, Object>> history = new ArrayList<>();
        for (int i = 0; i < MetricsKey.GC_HISTORY_MAX_KEEP; i++) {
            long eventKey = historyStart + i;
            long encoded = get(eventKey);
            if (encoded == 0) continue;
            long epochMillis  = encoded / 1_000_000L;
            long durationUs   = encoded % 1_000_000L;
            String[] gcMeta = parseGcMeta(Storage.self().gcEventHistory().get(eventKey));
            history.add(Map.of(
                "time",     TIME.format(Instant.ofEpochMilli(epochMillis)),
                "duration_ms", durationUs / 1000.0d,
                "type", "pause_notification",
                "gc_name", gcMeta[0],
                "gc_action", gcMeta[1],
                "gc_cause", gcMeta[2]
            ));
        }
        history.sort((a, b) -> b.get("time").toString().compareTo(a.get("time").toString()));
        return Map.of(
            "pause_count", count,
            "pause_history", history,
            "semantic", "jvm_gc_notification_pause"
        );
    }

    private String[] parseGcMeta(String raw) {
        if (raw == null || raw.isEmpty()) return new String[]{"", "", ""};

        String[] parts = raw.split(GC_META_SEPARATOR, -1);
        if (parts.length >= 3) return new String[]{parts[0], parts[1], parts[2]};
        if (parts.length == 2) return new String[]{parts[0], parts[1], ""};
        return new String[]{parts[0], "", ""};
    }

    private List<Map<String, Object>> getCombinedLatency() {
        // time -> label -> percentile -> value
        TreeMap<Long, Map<String, Map<Long, Long>>> rawData = new TreeMap<>(Collections.reverseOrder());

        Storage.self().latencyHistory().forEach((k, v) -> {
            long key = k;
            long metricKey = key / 1_000_000_000_000_000L;
            long rest = key % 1_000_000_000_000_000L;
            long p = rest / 1_000_000_000_000L;
            long t = rest % 1_000_000_000_000L;

            String label = null;
            if (metricKey == Math.abs(MetricsKey.LATENCY_MATCHING)) label = "matching";
            else if (metricKey == Math.abs(MetricsKey.LATENCY_TRANSPORT)) label = "transport";

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
            
            // 嚴格順序：先顯示 transport，再顯示 matching
            addMetrics(map, "transport", entry.getValue().get("transport"));
            addMetrics(map, "matching", entry.getValue().get("matching"));
            
            result.add(map);
        }
        return result;
    }

    private void addMetrics(Map<String, Object> target, String label, Map<Long, Long> pData) {
        if (pData == null) return;
        // 使用 LinkedHashMap 確保 p50 -> p99 -> p100 的順序
        Map<String, Object> sortedP = new LinkedHashMap<>();
        if (pData.containsKey(50L))  sortedP.put("p50",  pData.get(50L) + " ns");
        if (pData.containsKey(99L))  sortedP.put("p99",  pData.get(99L) + " ns");
        if (pData.containsKey(100L)) sortedP.put("p100", pData.get(100L) + " ns");
        target.put(label, sortedP);
    }

    @GetMapping("/balance")
    public Map<String, Object> getBalance(@RequestParam long userId, @RequestParam int assetId) {
        Balance b = Storage.self().balances().getUsing(new BalanceKey(userId, assetId), new Balance());
        return b == null ? Map.of("available", 0, "frozen", 0) : Map.of("available", b.getAvailable(), "frozen", b.getFrozen());
    }

    @GetMapping("/order_by_cid")
    public Order getOrderByCid(@RequestParam long userId, @RequestParam long cid) {
        LongValue orderId = Storage.self().clientOrderIdMap().get(new CidKey(userId, cid));
        if (orderId == null) return null;
        return Storage.self().orders().get(orderId);
    }
}
