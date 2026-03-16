package open.vincentf13.service.spot.matching.engine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.service.spot.model.Order;
import open.vincentf13.service.spot.model.Trade;
import org.springframework.stereotype.Component;

/** 
 * 核心回報器 (TPS 測試專用 - 移除同步日誌以追求極致性能)
 * 職責：將撮合結果非同步回傳或僅進行計數。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExecutionReporter implements AutoCloseable {

    // 壓測模式下應避免任何 log.debug/info，因為 SLF4J 即使不打印也會有 overhead
    public void reportAccepted(Order taker) {
        // TODO: 生產環境應寫入 Aeron 排隊回傳
    }

    public void reportRejected(long userId, long clientOrderId) {
        log.warn("[Rejected] UserId: {}, ClientOrderId: {}", userId, clientOrderId);
    }

    public void reportMatch(Order taker, Order maker, Trade trade) {
        // TODO: 生產環境應寫入 Aeron 排隊回傳
    }

    public void reportCancel(long userId, long orderId) {
        // TODO: 生產環境應寫入 Aeron 排隊回傳
    }

    public void reportCanceled(Order order) {
        // 相容舊介面，僅用於編譯通過
    }

    public void reportAuth(long userId) {
        // 僅用於編譯通過
    }

    public void reportDeposit(long userId, int assetId, long amount) {
        // 僅用於編譯通過
    }

    @Override
    public void close() {
        // 釋放資源
    }
}
