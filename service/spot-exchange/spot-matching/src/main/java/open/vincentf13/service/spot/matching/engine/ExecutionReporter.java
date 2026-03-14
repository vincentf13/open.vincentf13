package open.vincentf13.service.spot.matching.engine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.service.spot.model.Order;
import open.vincentf13.service.spot.sbe.OrderStatus;
import org.springframework.stereotype.Component;

/** 
 * 核心回報器 (TPS 測試專用 - 僅日誌輸出)
 * 職責：僅用於調試觀察撮合結果，不進行任何 I/O 或網絡傳輸
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExecutionReporter implements AutoCloseable {
    private boolean isReplaying = false;

    public void setReplaying(boolean replaying) { this.isReplaying = replaying; }

    public void reportAccepted(Order taker) {
        if (isReplaying) return;
        log.info("[Accepted] OrderId: {}, UserId: {}, ClientOrderId: {}", 
                taker.getOrderId(), taker.getUserId(), taker.getClientOrderId());
    }

    public void reportRejected(long userId, long clientOrderId) {
        if (isReplaying) return;
        log.warn("[Rejected] UserId: {}, ClientOrderId: {}", userId, clientOrderId);
    }

    public void reportCanceled(Order order) {
        if (isReplaying) return;
        log.info("[Canceled] OrderId: {}, UserId: {}, Filled: {}", 
                order.getOrderId(), order.getUserId(), order.getFilled());
    }

    public void reportTrade(Order order, long price, long qty) {
        if (isReplaying) return;
        log.info("[Trade] OrderId: {}, UserId: {}, Price: {}, Qty: {}, Status: {}", 
                order.getOrderId(), order.getUserId(), price, qty, 
                order.getStatus() == 2 ? "FILLED" : "PARTIALLY_FILLED");
    }

    public void reportAuth(long userId) {
        if (isReplaying) return;
        log.info("[Auth] UserId: {} Success", userId);
    }

    public void reportDeposit(long userId, int assetId, long amount) {
        if (isReplaying) return;
        log.info("[Deposit] UserId: {}, AssetId: {}, Amount: {}", userId, assetId, amount);
    }

    @Override public void close() {}
}
