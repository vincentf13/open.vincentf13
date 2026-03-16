package open.vincentf13.service.spot.matching.engine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.service.spot.model.Order;
import org.springframework.stereotype.Component;

/** 
 * 核心回報器 (TPS 測試專用 - 僅日誌輸出)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExecutionReporter implements AutoCloseable {

    public void reportAccepted(Order taker) {
        log.debug("[Accepted] OrderId: {}, UserId: {}, ClientOrderId: {}",
                taker.getOrderId(), taker.getUserId(), taker.getClientOrderId());
    }

    public void reportRejected(long userId, long clientOrderId) {
        log.warn("[Rejected] UserId: {}, ClientOrderId: {}", userId, clientOrderId);
    }

    public void reportCanceled(Order order) {
        log.debug("[Canceled] OrderId: {}, UserId: {}, Filled: {}",
                order.getOrderId(), order.getUserId(), order.getFilled());
    }

    public void reportTrade(Order order, long price, long qty) {
        log.debug("[Trade] OrderId: {}, UserId: {}, Price: {}, Qty: {}, Status: {}",
                order.getOrderId(), order.getUserId(), price, qty,
                order.getStatus() == 2 ? "FILLED" : "PARTIALLY_FILLED");
    }

    public void reportAuth(long userId) {
        log.debug("[Auth] UserId: {} Success", userId); // 全面降級為 debug
    }

    public void reportDeposit(long userId, int assetId, long amount) {
        log.debug("[Deposit] UserId: {}, AssetId: {}, Amount: {}", userId, assetId, amount); // 全面降級為 debug
    }
    @Override public void close() {}
}
