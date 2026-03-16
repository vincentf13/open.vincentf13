package open.vincentf13.service.spot.ws.controller;

import open.vincentf13.service.spot.infra.chronicle.Storage;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.wire.DocumentContext;
import net.openhft.chronicle.bytes.Bytes;
import open.vincentf13.service.spot.model.Balance;
import open.vincentf13.service.spot.model.BalanceKey;
import open.vincentf13.service.spot.model.Order;
import open.vincentf13.service.spot.model.CidKey;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.TreeMap;
import java.util.List;
import java.util.ArrayList;

/**
 * 異步驗證控制器：
 * 運行於 spot-ws-api 進程中，通過共享記憶體 (Chronicle Map) 讀取撮合引擎的即時狀態。
 * 避免了 Web 線程與撮合核心線程的 context switch 競爭。
 */
@RestController
@RequestMapping("/api/test")
public class TestVerificationController {

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
        Order order = Storage.self().orders().getUsing(orderId, new Order());
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
        Long orderId = Storage.self().clientOrderIdMap().get(cidKey);
        if (orderId == null) {
            return Map.of("error", "Order not found");
        }
        return getOrder(orderId);
    }

    @GetMapping("/metrics/tps")
    public List<Map<String, Object>> getTpsHistory() {
        // 使用 TreeMap 進行自動倒序排序
        TreeMap<Long, Long> sortedHistory = new TreeMap<>(java.util.Collections.reverseOrder());
        Storage.self().metricsHistory().forEach(sortedHistory::put);
        
        // 使用 List 固化順序，避免 JSON 解析器對 Map Key 自動重新排序
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        sortedHistory.forEach((timestamp, total) -> {
            if (timestamp > 0) {
                Map<String, Object> entry = new java.util.LinkedHashMap<>();
                entry.put("time", timestamp);
                entry.put("tps", total);
                result.add(entry);
            }
        });
        return result;
    }

    @GetMapping("/metrics/saturation")
    public Map<String, Object> getSaturation() {
        // 引擎核心指標
        long pollCount = Storage.self().metricsHistory().getOrDefault(Storage.KEY_POLL_COUNT, 0L);
        long workCount = Storage.self().metricsHistory().getOrDefault(Storage.KEY_WORK_COUNT, 0L);
        double ratio = pollCount == 0 ? 0 : (double) workCount / pollCount * 100.0;

        // 計算平均 TPS (數據開始 10 秒後的平均值)
        double averageTps = 0.0;
        TreeMap<Long, Long> sortedHistory = new TreeMap<>();
        Storage.self().metricsHistory().forEach((timestamp, total) -> {
            if (timestamp > 0) sortedHistory.put(timestamp, total);
        });

        if (sortedHistory.size() >= 2) {
            // 尋找第一個 TPS > 0 (即 WorkCount 開始增加) 的點
            long actualStartTime = -1;
            for (Map.Entry<Long, Long> entry : sortedHistory.entrySet()) {
                if (entry.getValue() > 0) {
                    actualStartTime = entry.getKey();
                    break;
                }
            }

            if (actualStartTime != -1) {
                long lastTime = sortedHistory.lastKey();
                long targetStartTime = actualStartTime + 10_000; // 從數據產生後 10s 開始

                // 尋找大於等於 targetStartTime 的第一個點作為起始基準
                Map.Entry<Long, Long> startEntry = sortedHistory.ceilingEntry(targetStartTime);
                if (startEntry != null && lastTime > startEntry.getKey()) {
                    long t1 = startEntry.getKey();
                    long w1 = startEntry.getValue();
                    long t2 = lastTime;
                    long w2 = sortedHistory.get(t2);

                    // (Work2 - Work1) / (Time2 - Time1) * 1000 (ms to s)
                    averageTps = (double) (w2 - w1) / (t2 - t1) * 1000.0;
                }
            }
        }

        // 全鏈路瓶頸指標
        long nettyRecvCount = Storage.self().metricsHistory().getOrDefault(Storage.KEY_NETTY_RECV_COUNT, 0L);
        long gatewayWalWriteCount = Storage.self().metricsHistory().getOrDefault(Storage.KEY_GATEWAY_WAL_WRITE_COUNT, 0L);
        long aeronSendCount = Storage.self().metricsHistory().getOrDefault(Storage.KEY_AERON_SEND_COUNT, 0L);
        long aeronRecvCount = Storage.self().metricsHistory().getOrDefault(Storage.KEY_AERON_RECV_COUNT, 0L);
        long aeronBackpressure = Storage.self().metricsHistory().getOrDefault(Storage.KEY_AERON_BACKPRESSURE, 0L);

        // JVM 與 OS 資源指標
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long allocatedMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = allocatedMemory - freeMemory;
        double memoryUsageRatio = maxMemory == 0 ? 0 : (double) usedMemory / maxMemory * 100.0;

        java.lang.management.OperatingSystemMXBean osBean = java.lang.management.ManagementFactory.getOperatingSystemMXBean();
        double sysLoad = -1.0;
        if (osBean instanceof com.sun.management.OperatingSystemMXBean sunOsBean) {
            sysLoad = sunOsBean.getCpuLoad() * 100.0; // 系統 CPU 負載
        }

        Map<String, Object> metrics = new java.util.LinkedHashMap<>();
        metrics.put("netty_recv_count", nettyRecvCount);
        metrics.put("gateway_wal_write_count", gatewayWalWriteCount);
        metrics.put("aeron_send_count", aeronSendCount);
        metrics.put("aeron_recv_count", aeronRecvCount);
        metrics.put("aeron_backpressure_count", aeronBackpressure);
        metrics.put("engine_work_count", workCount);
        metrics.put("engine_poll_count", pollCount);
        metrics.put("engine_saturation", String.format("%.2f%%", ratio));
        metrics.put("average_tps", String.format("%.2f", averageTps));

        metrics.put("jvm_memory_used_mb", usedMemory / (1024 * 1024));
        metrics.put("jvm_memory_max_mb", maxMemory / (1024 * 1024));
        metrics.put("jvm_memory_usage", String.format("%.2f%%", memoryUsageRatio));

        metrics.put("os_cpu_load", sysLoad >= 0 ? String.format("%.2f%%", sysLoad) : "N/A");

        return metrics;
    }
    @GetMapping("/dump_wal")
    public List<String> dumpWal() {
        List<String> logs = new ArrayList<>();
        ChronicleQueue queue = Storage.self().gatewaySenderWal();
        ExcerptTailer tailer = queue.createTailer();
        while (true) {
            try (DocumentContext dc = tailer.readingDocument()) {
                if (!dc.isPresent()) break;
                net.openhft.chronicle.bytes.Bytes<?> bytes = dc.wire().bytes();
                long remaining = bytes.readRemaining();
                if (remaining < 16) {
                    // 診斷：輸出 Hex
                    StringBuilder hex = new StringBuilder();
                    long pos = bytes.readPosition();
                    for (int i = 0; i < Math.min(remaining, 16); i++) {
                        hex.append(String.format("%02X ", bytes.readByte(pos + i)));
                    }
                    logs.add(String.format("Index: %d [Invalid] Len: %d, Hex: %s", dc.index(), remaining, hex.toString().trim()));
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
