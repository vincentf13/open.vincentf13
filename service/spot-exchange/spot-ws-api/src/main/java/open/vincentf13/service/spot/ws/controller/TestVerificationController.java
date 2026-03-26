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
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    @GetMapping("/metrics/tps")
    public List<Map<String, Object>> getTpsHistory() {
        TreeMap<Long, Long> sorted = new TreeMap<>();
        Storage.self().tpsHistory().forEach((k, v) -> sorted.put(k.getValue(), v.getValue()));
        
        List<Map<String, Object>> result = new ArrayList<>();
        Long lastTotal = null;
        for (var entry : sorted.entrySet()) {
            if (lastTotal != null) {
                long tps = entry.getValue() - lastTotal;
                if (tps >= 0) {
                    result.add(Map.of("time", TIME.format(Instant.ofEpochMilli(entry.getKey())), "tps", tps, "total", entry.getValue()));
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

        m.put("matching_jvm", formatJvm(MetricsKey.MATCHING_JVM_USED_MB, MetricsKey.MATCHING_JVM_MAX_MB));
        m.put("matching_gc", formatGc(MetricsKey.MATCHING_GC_COUNT, MetricsKey.MATCHING_GC_LAST_DURATION_MS));
        m.put("gateway_jvm", formatJvm(MetricsKey.GATEWAY_JVM_USED_MB, MetricsKey.GATEWAY_JVM_MAX_MB));
        m.put("gateway_gc", formatGc(MetricsKey.GATEWAY_GC_COUNT, MetricsKey.GATEWAY_GC_LAST_DURATION_MS));

        Map<String, List<Integer>> cpuIds = new LinkedHashMap<>();
        cpuIds.put("matching_engine", parseCpuMask(get(MetricsKey.CPU_ID_ENGINE)));
        cpuIds.put("matching_receiver", parseCpuMask(get(MetricsKey.CPU_ID_AERON_RECEIVER)));
        cpuIds.put("gateway_sender", parseCpuMask(get(MetricsKey.CPU_ID_AERON_SENDER)));
        cpuIds.put("netty_boss", parseCpuMask(get(MetricsKey.CPU_ID_NETTY_BOSS)));
        cpuIds.put("netty_worker_1", parseCpuMask(get(MetricsKey.CPU_ID_NETTY_WORKER_1)));
        m.put("cpu_affinity_history", cpuIds);
        
        m.put("latency", Map.of(
            "matching", getLatency(MetricsKey.LATENCY_MATCHING),
            "transport", getLatency(MetricsKey.LATENCY_TRANSPORT)
        ));

        return m;
    }

    private long get(long key) {
        var v = Storage.self().latestMetrics().get(new LongValue(key));
        return v == null ? 0 : v.getValue();
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

    private String formatGc(long countKey, long durationKey) {
        long count = get(countKey);
        return count == 0 ? "No GC yet" : String.format("%d次 (最近耗時:%dms)", count, get(durationKey));
    }

    private List<Map<String, Object>> getLatency(long metricKey) {
        TreeMap<Long, Map<String, Object>> history = new TreeMap<>(Collections.reverseOrder());
        long base = Math.abs(metricKey) * 1_000_000_000_000_000L;
        
        Storage.self().latencyHistory().forEach((k, v) -> {
            long key = k.getValue();
            if (key >= base && key < base + 1_000_000_000_000_000L) {
                long p = (key - base) / 1_000_000_000_000L;
                long t = (key - base) % 1_000_000_000_000L;
                history.computeIfAbsent(t, time -> {
                    var map = new LinkedHashMap<String, Object>();
                    map.put("time", TIME.format(Instant.ofEpochSecond(time)));
                    return map;
                }).put("p" + p, v.getValue() + " ns");
            }
        });
        return new ArrayList<>(history.values());
    }

    @GetMapping("/balance")
    public Map<String, Object> getBalance(@RequestParam long userId, @RequestParam int assetId) {
        Balance b = Storage.self().balances().getUsing(new BalanceKey(userId, assetId), new Balance());
        return b == null ? Map.of("available", 0, "frozen", 0) : Map.of("available", b.getAvailable(), "frozen", b.getFrozen());
    }
}
