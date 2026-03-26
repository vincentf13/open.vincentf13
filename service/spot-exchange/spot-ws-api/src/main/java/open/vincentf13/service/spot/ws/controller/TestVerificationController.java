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
        TreeMap<Long, Long> sorted = new TreeMap<>(Collections.reverseOrder());
        Storage.self().tpsHistory().forEach((k, v) -> sorted.put(k.getValue(), v.getValue()));
        
        List<Map<String, Object>> result = new ArrayList<>();
        sorted.forEach((ts, total) -> {
            var prev = sorted.higherEntry(ts);
            long tps = (prev == null) ? 0 : total - prev.getValue();
            if (tps >= 0) {
                result.add(Map.of("time", TIME.format(Instant.ofEpochMilli(ts)), "total", total, "tps", tps));
            }
        });
        return result;
    }

    @GetMapping("/metrics/saturation")
    public Map<String, Object> getSaturation() {
        Storage s = Storage.self();
        Map<String, Object> m = new LinkedHashMap<>();
        
        // 核心計數
        m.put("netty_recv", get(MetricsKey.NETTY_RECV_COUNT));
        m.put("wal_write", get(MetricsKey.GATEWAY_WAL_WRITE_COUNT));
        m.put("aeron_send", get(MetricsKey.AERON_SEND_COUNT));
        m.put("backpressure", get(MetricsKey.AERON_BACKPRESSURE));

        // 資源占用
        m.put("matching_jvm", formatJvm(MetricsKey.MATCHING_JVM_USED_MB, MetricsKey.MATCHING_JVM_MAX_MB));
        m.put("gateway_jvm", formatJvm(MetricsKey.GATEWAY_JVM_USED_MB, MetricsKey.GATEWAY_JVM_MAX_MB));
        
        // 延遲歷史 (10s Intervals)
        m.put("latency_history", Map.of(
            "matching", getLatency(MetricsKey.LATENCY_MATCHING),
            "transport", getLatency(MetricsKey.LATENCY_TRANSPORT)
        ));

        return m;
    }

    private long get(long key) {
        var v = Storage.self().latestMetrics().get(new LongValue(key));
        return v == null ? 0 : v.getValue();
    }

    private String formatJvm(long usedKey, long maxKey) {
        long u = get(usedKey), m = get(maxKey);
        return String.format("%dMB (%.1f%%)", u, (double) u / (m == 0 ? 1 : m) * 100);
    }

    private List<Map<String, Object>> getLatency(long metricKey) {
        TreeMap<Long, Map<String, Object>> history = new TreeMap<>(Collections.reverseOrder());
        long base = Math.abs(metricKey) * 1_000_000_000_000_000L;
        
        Storage.self().latencyHistory().forEach((k, v) -> {
            long key = k.getValue();
            if (key >= base && key < base + 1_000_000_000_000_000L) {
                long percentile = (key - base) / 1_000_000_000_000L;
                long windowSec = (key - base) % 1_000_000_000_000L;
                
                var entry = history.computeIfAbsent(windowSec, t -> {
                    var map = new LinkedHashMap<String, Object>();
                    map.put("time", TIME.format(Instant.ofEpochSecond(t)));
                    return map;
                });
                entry.put("p" + percentile, v.getValue() + " ns");
            }
        });
        return new ArrayList<>(history.values());
    }

    // --- 輔助業務接口 ---
    @GetMapping("/balance")
    public Map<String, Object> getBalance(@RequestParam long userId, @RequestParam int assetId) {
        Balance b = Storage.self().balances().getUsing(new BalanceKey(userId, assetId), new Balance());
        return b == null ? Map.of("available", 0, "frozen", 0) : Map.of("available", b.getAvailable(), "frozen", b.getFrozen());
    }
}
