package open.vincentf13.service.spot.matching.engine;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 系統指令處理器 (System Processor)
 職責：處理與交易撮合無關的系統級指令，如身份認證、資產初始化等
 */
@Slf4j
@Component
public class SystemProcessor {
    private final Ledger ledger;
    private final ExecutionReporter reporter;

    public SystemProcessor(Ledger ledger, ExecutionReporter reporter) {
        this.ledger = ledger;
        this.reporter = reporter;
    }

    /** 
      處理用戶認證
      邏輯：初始化用戶資產表並回傳成功訊號
     */
    public void handleAuth(long userId, long gwSeq, boolean isReplaying) {
        // 初始化基本資產帳戶 (BTC/USDT)
        ledger.initBalance(userId, Asset.BTC, gwSeq); 
        ledger.initBalance(userId, Asset.USDT, gwSeq);
        
        // 發送認證成功回報
        reporter.sendAuthSuccess(userId, gwSeq, isReplaying);
        
        if (!isReplaying) {
            log.info("用戶 {} 認證成功 (gwSeq: {})", userId, gwSeq);
        }
    }
}
