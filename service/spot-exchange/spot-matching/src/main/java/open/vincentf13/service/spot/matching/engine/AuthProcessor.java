package open.vincentf13.service.spot.matching.engine;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 系統指令處理器 (AuthProcessor)
 職責：處理身份認證、資產初始化等非交易撮合指令
 */
@Slf4j
@Component
public class AuthProcessor {
    private final Ledger ledger;
    private final ExecutionReporter reporter;

    public AuthProcessor(Ledger ledger, ExecutionReporter reporter) {
        this.ledger = ledger;
        this.reporter = reporter;
    }

    /** 
      處理用戶認證 
     */
    public void handleAuth(long userId, long gwSeq) {
        // 1. 初始化資產帳戶
        ledger.initAccount(userId, Asset.BTC, gwSeq); 
        ledger.initAccount(userId, Asset.USDT, gwSeq);
        
        // 2. 發送認證成功回報
        reporter.sendAuthSuccess(userId, gwSeq);
        
        // 3. 關鍵修正：立即沖刷回報流，確保客戶端即時收到結果
        reporter.flushBatch(gwSeq);
        
        log.debug("用戶 {} 認證處理完成 (gwSeq: {})", userId, gwSeq);
    }
}
