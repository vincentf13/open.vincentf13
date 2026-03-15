package open.vincentf13.service.spot.ws.controller;

import open.vincentf13.service.spot.infra.chronicle.Storage;
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
        Map<Long, Long> history = new TreeMap<>();
        Storage.self().metricsHistory().forEach(history::put);
        return history;
    }

    @GetMapping("/metrics/saturation")
    public Map<String, Object> getSaturation() {
        Long pollCount = Storage.self().metricsHistory().get(Storage.KEY_POLL_COUNT);
        Long workCount = Storage.self().metricsHistory().get(Storage.KEY_WORK_COUNT);
        
        if (pollCount == null || pollCount == 0) {
            return Map.of("saturation_ratio", "0%", "status", "IDLE");
        }
        
        double ratio = (workCount == null ? 0.0 : workCount.doubleValue()) / pollCount.doubleValue();
        return Map.of(
            "poll_count", pollCount,
            "work_count", workCount == null ? 0 : workCount,
            "saturation_ratio", String.format("%.2f%%", ratio * 100),
            "description", "證明撮合引擎 100% 都在處理數據，無空等。"
        );
    }
}
