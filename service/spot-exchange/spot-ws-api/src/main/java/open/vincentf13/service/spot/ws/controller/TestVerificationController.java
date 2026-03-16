package open.vincentf13.service.spot.ws.controller;

import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.wire.DocumentContext;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.model.Balance;
import open.vincentf13.service.spot.model.BalanceKey;
import open.vincentf13.service.spot.model.CidKey;
import open.vincentf13.service.spot.model.Order;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

import static open.vincentf13.service.spot.infra.Constants.*;

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
        // 使用 TreeMap 進行自動倒序排序 (最新在前面)
        TreeMap<Long, Long> sortedHistory = new TreeMap<>(Collections.reverseOrder());
        Storage.self().metricsHistory().forEach((timestamp, total) -> {
            if (timestamp > 0) sortedHistory.put(timestamp, total);
        });
        
        List<Map<String, Object>> result = new ArrayList<>();
        
        // 遍歷排序後的歷史記錄
        sortedHistory.forEach((timestamp, total) -> {
            // 尋找比當前 timestamp 小的最大 timestamp (即前一秒)
            Map.Entry<Long, Long> previousEntry = sortedHistory.higherEntry(timestamp);
            long diff = (previousEntry == null) ? total : total - previousEntry.getValue();
            
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("time", timestamp);
            entry.put("total", total);
            entry.put("diff", diff);
            result.add(entry);
        });
        return result;
    }

    @GetMapping("/metrics/saturation")
    public Map<String, Object> getSaturation() {
        // 引擎核心指標
        long pollCount = Storage.self().metricsHistory().getOrDefault(MetricsKey.POLL_COUNT, 0L);
        long workCount = Storage.self().metricsHistory().getOrDefault(MetricsKey.WORK_COUNT, 0L);
        double ratio = pollCount == 0 ? 0 : (double) workCount / pollCount * 100.0;

        // 全鏈路瓶頸指標
        long nettyRecvCount = Storage.self().metricsHistory().getOrDefault(MetricsKey.NETTY_RECV_COUNT, 0L);
        long gatewayWalWriteCount = Storage.self().metricsHistory().getOrDefault(MetricsKey.GATEWAY_WAL_WRITE_COUNT, 0L);
        long aeronSendCount = Storage.self().metricsHistory().getOrDefault(MetricsKey.AERON_SEND_COUNT, 0L);
        long aeronBackpressure = Storage.self().metricsHistory().getOrDefault(MetricsKey.AERON_BACKPRESSURE, 0L);

        // JVM 與 OS 資源指標 (從 MetricsHistory 獲取異步更新的數值)
        long matchingJvmUsed = Storage.self().metricsHistory().getOrDefault(MetricsKey.MATCHING_JVM_USED_MB, 0L);
        long gatewayJvmUsed = Storage.self().metricsHistory().getOrDefault(MetricsKey.GATEWAY_JVM_USED_MB, 0L);
        long matchingCpuLoad = Storage.self().metricsHistory().getOrDefault(MetricsKey.MATCHING_CPU_LOAD, 0L);
        long gatewayCpuLoad = Storage.self().metricsHistory().getOrDefault(MetricsKey.GATEWAY_CPU_LOAD, 0L);

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("netty_recv_count", nettyRecvCount);
        metrics.put("gateway_wal_write_count", gatewayWalWriteCount);
        metrics.put("aeron_send_count", aeronSendCount);
        metrics.put("aeron_backpressure_count", aeronBackpressure);
        metrics.put("engine_work_count", workCount);
        metrics.put("engine_poll_count", pollCount);
        metrics.put("engine_saturation", String.format("%.2f%%", ratio));

        metrics.put("matching_jvm_used_mb", matchingJvmUsed);
        metrics.put("gateway_jvm_used_mb", gatewayJvmUsed);
        metrics.put("matching_cpu_load", String.format("%d%%", matchingCpuLoad));
        metrics.put("gateway_cpu_load", String.format("%d%%", gatewayCpuLoad));

        // CPU Affinity 綁定資訊 (按服務聚合)
        Map<String, Object> cpuAffinity = new LinkedHashMap<>();
        
        // 1. Spot Matching 服務
        Map<String, Object> matchingAffinity = new LinkedHashMap<>();
        addCpuMetric(matchingAffinity, "engine", MetricsKey.CPU_ID_ENGINE);
        addCpuMetric(matchingAffinity, "aeron_receiver", MetricsKey.CPU_ID_AERON_RECEIVER);
        cpuAffinity.put("spot-matching", matchingAffinity);

        // 2. Spot WS-API 服務
        Map<String, Object> wsApiAffinity = new LinkedHashMap<>();
        addCpuMetric(wsApiAffinity, "netty_worker_1", MetricsKey.CPU_ID_NETTY_WORKER_1);
        addCpuMetric(wsApiAffinity, "netty_worker_2", MetricsKey.CPU_ID_NETTY_WORKER_2);
        addCpuMetric(wsApiAffinity, "async_wal_writer", MetricsKey.CPU_ID_WAL_WRITER);
        addCpuMetric(wsApiAffinity, "aeron_sender", MetricsKey.CPU_ID_AERON_SENDER);
        cpuAffinity.put("spot-ws-api", wsApiAffinity);

        metrics.put("cpu_affinity", cpuAffinity);

        return metrics;
    }

    private void addCpuMetric(Map<String, Object> map, String name, long key) {
        Long cpuId = Storage.self().metricsHistory().get(key);
        if (cpuId != null) map.put(name, "Core " + cpuId);
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
