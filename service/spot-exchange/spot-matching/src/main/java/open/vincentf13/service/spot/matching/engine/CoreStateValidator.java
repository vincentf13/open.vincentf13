package open.vincentf13.service.spot.matching.engine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
}
