package open.vincentf13.service.spot.matching.engine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.sbe.OrderStatus;
import org.springframework.stereotype.Component;
import net.openhft.chronicle.map.ChronicleMap;

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
        // 1. 執行帳務增加 (Ledger 內部具備 seq 冪等保護)
        ledger.increaseAvailable(userId, assetId, amount, gwSeq);
        
        // 2. 發送執行回報 (充值成功)
        reporter.reportDeposit(userId, assetId, amount);
        
        log.info("用戶 {} 充值成功：資產={}, 數量={}, gwSeq={}", userId, assetId, amount, gwSeq);
    }
}
