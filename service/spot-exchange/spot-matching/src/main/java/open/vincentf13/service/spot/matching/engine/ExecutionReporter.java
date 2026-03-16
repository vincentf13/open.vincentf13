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
    private boolean isReplaying = false;

    public void setReplaying(boolean replaying) { this.isReplaying = replaying; }

    public void reportAccepted(Order taker) {
        if (isReplaying) return;
        log.debug("[Accepted] OrderId: {}, UserId: {}, ClientOrderId: {}",
                taker.getOrderId(), taker.getUserId(), taker.getClientOrderId());
    }

    public void reportRejected(long userId, long clientOrderId) {
        if (isReplaying) return;
        log.warn("[Rejected] UserId: {}, ClientOrderId: {}", userId, clientOrderId);
    }

    public void reportCanceled(Order order) {
        if (isReplaying) return;
        log.debug("[Canceled] OrderId: {}, UserId: {}, Filled: {}",
                order.getOrderId(), order.getUserId(), order.getFilled());
    }

    public void reportTrade(Order order, long price, long qty) {
        if (isReplaying) return;
        log.debug("[Trade] OrderId: {}, UserId: {}, Price: {}, Qty: {}, Status: {}",
                order.getOrderId(), order.getUserId(), price, qty,
                order.getStatus() == 2 ? "FILLED" : "PARTIALLY_FILLED");
    }

    public void reportAuth(long userId) {
        if (isReplaying) return;
        log.info("[Auth] UserId: {} Success", userId); // 驗證次數少，保留 info
    }

    public void reportDeposit(long userId, int assetId, long amount) {
        if (isReplaying) return;
        log.info("[Deposit] UserId: {}, AssetId: {}, Amount: {}", userId, assetId, amount); // 充值次數少，保留 info
    }
    @Override public void close() {}
}
