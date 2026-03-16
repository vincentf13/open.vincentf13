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
    public Map<Long, Long> getTpsHistory() {
        // 使用 TreeMap 進行自動倒序排序
        TreeMap<Long, Long> sortedHistory = new TreeMap<>(java.util.Collections.reverseOrder());
        Storage.self().metricsHistory().forEach(sortedHistory::put);
        
        // 使用 LinkedHashMap 固化順序，且輸出格式為簡約的 { "timestamp": total }
        Map<Long, Long> result = new java.util.LinkedHashMap<>();
        sortedHistory.forEach((timestamp, total) -> {
            if (timestamp > 0) {
                result.put(timestamp, total);
            }
        });
        return result;
    }

    @GetMapping("/metrics/saturation")
    public Map<String, Object> getSaturation() {
        // 從 metricsHistory 中提取特殊的負數 Key
        long pollCount = Storage.self().metricsHistory().getOrDefault(Storage.KEY_POLL_COUNT, 0L);
        long workCount = Storage.self().metricsHistory().getOrDefault(Storage.KEY_WORK_COUNT, 0L);
        double ratio = pollCount == 0 ? 0 : (double) workCount / pollCount * 100.0;
        
        return Map.of(
            "poll_count", pollCount,
            "work_count", workCount,
            "saturation_ratio", String.format("%.2f%%", ratio)
        );
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
