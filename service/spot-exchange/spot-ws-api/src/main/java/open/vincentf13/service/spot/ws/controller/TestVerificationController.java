package open.vincentf13.service.spot.ws.controller;

import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.wire.DocumentContext;
import open.vincentf13.service.spot.infra.chronicle.LongValue;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.model.Balance;
import open.vincentf13.service.spot.model.BalanceKey;
import open.vincentf13.service.spot.model.CidKey;
import open.vincentf13.service.spot.model.Order;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static open.vincentf13.service.spot.infra.Constants.*;

/**
 * 異步驗證控制器：
 */
@RestController
@RequestMapping("/api/test")
public class TestVerificationController {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
            .withZone(ZoneId.systemDefault());

    @GetMapping("/balance")
    public Map<String, Object> getBalance(@RequestParam long userId, @RequestParam int assetId) {
        BalanceKey key = new BalanceKey(userId, assetId);
        Balance balance = Storage.self().balances().getUsing(key, new Balance());
        if (balance == null) {
            return Map.of("available", 0L, "frozen", 0L);
        }
        return Map.of("available", balance.getAvailable(), "frozen", balance.getFrozen());
    }

    @GetMapping("/order")
    public Map<String, Object> getOrder(@RequestParam long orderId) {
        Order order = Storage.self().orders().getUsing(new LongValue(orderId), new Order());
        if (order == null) {
            return Map.of("error", "Order not found");
        }
        return Map.of(
            "orderId", order.getOrderId(),
            "userId", order.getUserId(),
            "status", order.getStatus(),
            "price", order.getPrice(),
            "qty", order.getQty(),
            "filled", order.getFilled()
        );
    }
    
    @GetMapping("/order_by_cid")
    public Map<String, Object> getOrderByCid(@RequestParam long userId, @RequestParam long cid) {
        CidKey cidKey = new CidKey();
        cidKey.set(userId, cid);
        LongValue orderIdVal = Storage.self().clientOrderIdMap().get(cidKey);
        if (orderIdVal == null) {
            return Map.of("error", "Order not found");
        }
        return getOrder(orderIdVal.getValue());
    }

    @GetMapping("/metrics/tps")
    public List<Map<String, Object>> getTpsHistory() {
        TreeMap<Long, Long> sortedHistory = new TreeMap<>(Collections.reverseOrder());
        Storage.self().metricsHistory().forEach((key, total) -> {
            if (key.getValue() > 1000000) sortedHistory.put(key.getValue(), total.getValue());
        });
        
        List<Map<String, Object>> result = new ArrayList<>();
        sortedHistory.forEach((timestamp, total) -> {
            Map.Entry<Long, Long> previousEntry = sortedHistory.higherEntry(timestamp);
            long diff = (previousEntry == null) ? 0 : total - previousEntry.getValue();
            
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("time", timestamp);
            entry.put("total", total);
            entry.put("tps", diff);
            result.add(entry);
        });
        return result;
    }

    private long getMetric(long key, long defaultValue) {
        LongValue val = Storage.self().metricsHistory().get(new LongValue(key));
        return val != null ? val.getValue() : defaultValue;
    }

    @GetMapping("/metrics/saturation")
    public Map<String, Object> getSaturation() {
        
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("netty_recv_count", getMetric(MetricsKey.NETTY_RECV_COUNT, 0L));
        metrics.put("gateway_wal_write_count", getMetric(MetricsKey.GATEWAY_WAL_WRITE_COUNT, 0L));
        metrics.put("aeron_send_count", getMetric(MetricsKey.AERON_SEND_COUNT, 0L));
        metrics.put("aeron_backpressure_count", getMetric(MetricsKey.AERON_BACKPRESSURE, 0L));

        metrics.put("matching_jvm_used", String.format("%dMB (%.1f%%)", getMetric(MetricsKey.MATCHING_JVM_USED_MB, 0L), (double) getMetric(MetricsKey.MATCHING_JVM_USED_MB, 0L) / getMetric(MetricsKey.MATCHING_JVM_MAX_MB, 1L) *100));
        metrics.put("gateway_jvm_used", String.format("%dMB (%.1f%%)", getMetric(MetricsKey.GATEWAY_JVM_USED_MB, 0L), (double) getMetric(MetricsKey.GATEWAY_JVM_USED_MB, 0L) / getMetric(MetricsKey.GATEWAY_JVM_MAX_MB, 1L) *100));
        metrics.put("matching_cpu_load", String.format("%d%%", getMetric(MetricsKey.MATCHING_AERON_RECEVIER_WORKER_DUTY_CYCLE, 0L)));
        metrics.put("gateway_cpu_load", String.format("%d%%", getMetric(MetricsKey.GATEWAY_AERON_SENDER_WORKER_DUTY_CYCLE, 0L)));

        var os = java.lang.management.ManagementFactory.getOperatingSystemMXBean();
        if (os instanceof com.sun.management.OperatingSystemMXBean sunOs) {
            metrics.put("os_system_cpu_load", String.format("%.2f%%", sunOs.getCpuLoad() * 100));
            metrics.put("os_process_cpu_load", String.format("%.2f%%", sunOs.getProcessCpuLoad() * 100));
        }

        Map<String, Object> gcMetrics = new LinkedHashMap<>();
        
        Map<String, Object> matchingGc = new LinkedHashMap<>();
        matchingGc.put("count", getMetric(MetricsKey.MATCHING_GC_COUNT, 0L) + " times");
        matchingGc.put("last_interval", getMetric(MetricsKey.MATCHING_GC_LAST_INTERVAL_MS, 0L) + " ms");
        matchingGc.put("last_duration", getMetric(MetricsKey.MATCHING_GC_LAST_DURATION_MS, 0L) + " ms");
        matchingGc.put("history", getGcHistory(MetricsKey.MATCHING_GC_HISTORY_START));
        gcMetrics.put("spot-matching", matchingGc);

        Map<String, Object> gatewayGc = new LinkedHashMap<>();
        gatewayGc.put("count", getMetric(MetricsKey.GATEWAY_GC_COUNT, 0L) + " times");
        gatewayGc.put("last_interval", getMetric(MetricsKey.GATEWAY_GC_LAST_INTERVAL_MS, 0L) + " ms");
        gatewayGc.put("last_duration", getMetric(MetricsKey.GATEWAY_GC_LAST_DURATION_MS, 0L) + " ms");
        gatewayGc.put("history", getGcHistory(MetricsKey.GATEWAY_GC_HISTORY_START));
        gcMetrics.put("spot-ws-api", gatewayGc);

        metrics.put("gc_metrics", gcMetrics);

        Map<String, Object> cpuAffinity = new LinkedHashMap<>();
        Map<String, Object> matchingAffinity = new LinkedHashMap<>();
        addCpuMetric(matchingAffinity, "engine", MetricsKey.CPU_ID_ENGINE);
        cpuAffinity.put("spot-matching", matchingAffinity);

        Map<String, Object> wsApiAffinity = new LinkedHashMap<>();
        addCpuMetric(wsApiAffinity, "netty_worker_1", MetricsKey.CPU_ID_NETTY_WORKER_1);
        addCpuMetric(wsApiAffinity, "netty_worker_2", MetricsKey.CPU_ID_NETTY_WORKER_2);
        addCpuMetric(wsApiAffinity, "netty_worker_3", MetricsKey.CPU_ID_NETTY_WORKER_3);
        addCpuMetric(wsApiAffinity, "netty_worker_4", MetricsKey.CPU_ID_NETTY_WORKER_4);
        addCpuMetric(wsApiAffinity, "aeron_sender", MetricsKey.CPU_ID_AERON_SENDER);
        cpuAffinity.put("spot-ws-api", wsApiAffinity);

        metrics.put("cpu_affinity", cpuAffinity);

        return metrics;
    }

    private List<String> getGcHistory(long startKey) {
        List<Long> timestamps = new ArrayList<>();
        for (int i = 0; i < MetricsKey.GC_HISTORY_MAX_KEEP; i++) {
            LongValue val = Storage.self().metricsHistory().get(new LongValue(startKey - i));
            if (val != null && val.getValue() > 0) timestamps.add(val.getValue());
        }
        timestamps.sort(Collections.reverseOrder());
        
        List<String> history = new ArrayList<>();
        for (Long ts : timestamps) {
            history.add(TIME_FORMATTER.format(Instant.ofEpochMilli(ts)));
        }
        return history;
    }

    private void addCpuMetric(Map<String, Object> map, String name, long key) {
        LongValue val = Storage.self().metricsHistory().get(new LongValue(key));
        if (val != null) map.put(name, "Core " + val.getValue());
        else map.put(name, "Not Bound");
    }

    @GetMapping("/dump_wal")
    public List<String> dumpWal() {
        List<String> logs = new ArrayList<>();
        ChronicleQueue queue = Storage.self().gatewaySenderWal();
        ExcerptTailer tailer = queue.createTailer();
        while (true) {
            try (DocumentContext dc = tailer.readingDocument()) {
                if (!dc.isPresent()) break;
                Bytes<?> bytes = dc.wire().bytes();
                long remaining = bytes.readRemaining();
                if (remaining < 16) {
                    logs.add(String.format("Index: %d [Invalid] Len: %d", dc.index(), remaining));
                    continue;
                }
                int len = bytes.readInt();
                int msgType = bytes.readInt();
                long seq = bytes.readLong();
                logs.add(String.format("Index: %d, MsgType: %d, Seq: %d, DataLen: %d",
                    dc.index(), msgType, seq, len));
                bytes.readSkip((long) len - 12);
            }
        }
        return logs;
    }
}
