package open.vincentf13.service.spot.matching.engine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.model.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CoreStateValidator {
    private final Ledger ledger;

    public void validateOrThrow() {
        ledger.validateState();

        final int[] activeDiskCount = new int[1];
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

        log.info("核心狀態自校驗通過，activeOrders={}", inMemoryCount);
    }
}
