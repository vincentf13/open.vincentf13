package open.vincentf13.service.spot.matching.engine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 充值處理器 (DepositProcessor)
 職責：處理資金存入指令，並具備冪等性保護
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DepositProcessor {
    private final Ledger ledger;
    private final ExecutionReporter reporter;

    /** 
      處理充值指令
     */
    public void handleDeposit(long userId, int assetId, long amount, long gwSeq) {
        if (userId <= 0 || amount <= 0 || Asset.of(assetId) == null) {
            log.warn("忽略非法充值請求: uid={}, aid={}, amt={}, seq={}", userId, assetId, amount, gwSeq);
            return;
        }

        ledger.increaseAvailable(userId, assetId, amount, gwSeq);
        reporter.reportDeposit(userId, assetId, amount);
    }
}
