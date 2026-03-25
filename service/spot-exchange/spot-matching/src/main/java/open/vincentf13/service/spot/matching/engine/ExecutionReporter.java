package open.vincentf13.service.spot.matching.engine;

import lombok.extern.slf4j.Slf4j;
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

    public void reportAccepted(Order taker) {
        if (++acceptedCount % 1000 == 0) {
            log.info("[MATCHING-ACCEPTED] (Sampled 1/1000) UserId: {}, ClientOrderId: {}, Total: {}", taker.getUserId(), taker.getClientOrderId(), acceptedCount);
        }
    }

    public void reportMatch(Order taker, Order maker, Trade trade) { }

    public void reportCanceled(Order order) { }

    public void reportRejected(long userId, long clientOrderId) {
        if (++rejectedCount % 1000 == 0) {
            log.warn("[MATCHING-REJECTED] (Sampled 1/1000) UserId: {}, ClientOrderId: {}, Total: {}", userId, clientOrderId, rejectedCount);
        }
    }

    public void reportAuth(long userId) { }
    public void reportDeposit(long userId, int assetId, long amount) { }

    @Override
    public void close() { }
}
