package open.vincentf13.service.spot.matching.engine;

import lombok.extern.slf4j.Slf4j;
import open.vincentf13.service.spot.infra.Constants.MetricsKey;
import open.vincentf13.service.spot.infra.metrics.StaticMetricsHolder;
import open.vincentf13.service.spot.model.Order;
import open.vincentf13.service.spot.model.Trade;
import org.springframework.stereotype.Component;

/** 
 * 核心回報器 (極簡版 - 僅保留介面以確保編譯)
 * 職責：作為撮合結果的出口，目前僅執行空操作以降低壓測干擾
 */
@Slf4j
@Component
public class ExecutionReporter implements AutoCloseable {

    private long rejectedCount = 0;
    private long acceptedCount = 0;
    private long matchedCount = 0;
    private long canceledCount = 0;
    private long authCount = 0;
    private long depositCount = 0;

    public void reportAccepted(Order taker) {
        StaticMetricsHolder.addCounter(MetricsKey.ORDER_ACCEPTED_COUNT, 1);
        if (++acceptedCount % 1000 == 0) {
            log.info("[MATCHING-ACCEPTED] (Sampled 1/1000) UserId: {}, ClientOrderId: {}, Total: {}", taker.getUserId(), taker.getClientOrderId(), acceptedCount);
        }
    }

    public void reportMatch(Order taker, Order maker, Trade trade) {
        if (++matchedCount % 1000 == 0) {
            log.info(
                "[MATCHING-MATCHED] (Sampled 1/1000) TradeId: {}, MakerOrderId: {}, TakerOrderId: {}, Price: {}, Qty: {}, Total: {}",
                trade.getTradeId(), maker.getOrderId(), taker.getOrderId(), trade.getPrice(), trade.getQty(), matchedCount
            );
        }
    }

    public void reportCanceled(Order order) {
        if (++canceledCount % 1000 == 0) {
            log.info("[MATCHING-CANCELED] (Sampled 1/1000) OrderId: {}, UserId: {}, Total: {}", order.getOrderId(), order.getUserId(), canceledCount);
        }
    }

    public void reportRejected(long userId, long clientOrderId) {
        StaticMetricsHolder.addCounter(MetricsKey.ORDER_REJECTED_COUNT, 1);
        if (++rejectedCount % 1000 == 0) {
            log.warn("[MATCHING-REJECTED] (Sampled 1/1000) UserId: {}, ClientOrderId: {}, Total: {}", userId, clientOrderId, rejectedCount);
        }
    }

    public void reportAuth(long userId) {
        if (++authCount % 1000 == 0) {
            log.info("[MATCHING-AUTH] (Sampled 1/1000) UserId: {}, Total: {}", userId, authCount);
        }
    }

    public void reportDeposit(long userId, int assetId, long amount) {
        if (++depositCount % 1000 == 0) {
            log.info("[MATCHING-DEPOSIT] (Sampled 1/1000) UserId: {}, AssetId: {}, Amount: {}, Total: {}", userId, assetId, amount, depositCount);
        }
    }

    @Override
    public void close() {
        log.info(
            "ExecutionReporter closing: accepted={}, rejected={}, matched={}, canceled={}, auth={}, deposit={}",
            acceptedCount, rejectedCount, matchedCount, canceledCount, authCount, depositCount
        );
    }
}
