package open.vincentf13.service.spot.matching.engine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.infra.Constants.OrderSide;
import open.vincentf13.service.spot.model.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class CoreStateValidator {
    private final Ledger ledger;

    public void validateOnRecovery() {
        try {
            basicValidateOnRecovery();
        } catch (RuntimeException ex) {
            log.warn("冷啟動自校驗未通過，改由 WAL 重放收斂最終一致性: {}", ex.getMessage(), ex);
        }
    }

    public void basicValidateOnRecovery() {
        ledger.validateState();

        for (OrderBook book : OrderBook.getInstances()) {
            book.validateState();
        }

        log.info("冷啟動基礎自校驗完成，orderBooks={}", OrderBook.getInstances().size());
    }

    public void validateOrThrow() {
        deepValidateOrThrow();
    }

    public void deepValidateOrThrow() {
        ledger.validateState();

        final int[] activeDiskCount = new int[1];
        Map<Long, Long> expectedFrozenByAccountAsset = new HashMap<>();
        Storage.self().activeOrders().forEach((orderId, active) -> {
            activeDiskCount[0]++;
            Order order = Storage.self().orders().getUsing(orderId, new Order());
            if (order == null) {
                throw new IllegalStateException("Active order index points to missing order, orderId=" + orderId.getValue());
            }
            if (order.isTerminal()) {
                throw new IllegalStateException("Active order index points to terminal order, orderId=" + order.getOrderId());
            }
            if (!OrderBook.get(order.getSymbolId()).hasOrder(order.getOrderId())) {
                throw new IllegalStateException("Active disk order missing from in-memory book, orderId=" + order.getOrderId());
            }
            accumulateExpectedFrozen(expectedFrozenByAccountAsset, order);
        });

        int inMemoryCount = 0;
        for (OrderBook book : OrderBook.getInstances()) {
            book.validateState();
            inMemoryCount += book.activeOrderCount();
        }

        if (inMemoryCount != activeDiskCount[0]) {
            throw new IllegalStateException(
                "Active order count mismatch, memory=%d, disk=%d".formatted(inMemoryCount, activeDiskCount[0])
            );
        }
        ledger.validateFrozenBalances(expectedFrozenByAccountAsset);

        log.info("核心狀態自校驗通過，activeOrders={}", inMemoryCount);
    }

    private void accumulateExpectedFrozen(Map<Long, Long> expectedFrozenByAccountAsset, Order order) {
        OrderBook book = OrderBook.get(order.getSymbolId());
        int assetId = order.getSide() == OrderSide.BUY ? book.getQuoteAssetId() : book.getBaseAssetId();
        long combinedKey = combine(order.getUserId(), assetId);
        expectedFrozenByAccountAsset.merge(combinedKey, order.getFrozen(), Long::sum);
    }

    private long combine(long userId, int assetId) {
        return (userId << 32) | (assetId & 0xFFFFFFFFL);
    }
}
