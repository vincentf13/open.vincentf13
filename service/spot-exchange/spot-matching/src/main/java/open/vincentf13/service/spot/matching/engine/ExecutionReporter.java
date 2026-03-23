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

    public void reportAccepted(Order taker) { }

    public void reportMatch(Order taker, Order maker, Trade trade) { }

    public void reportCanceled(Order order) { }

    public void reportRejected(long userId, long clientOrderId) {
        log.warn("[MATCHING-REJECTED] UserId: {}, ClientOrderId: {}", userId, clientOrderId);
    }

    public void reportAuth(long userId) { }
    public void reportDeposit(long userId, int assetId, long amount) { }

    @Override
    public void close() { }
}
