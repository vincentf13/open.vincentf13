package open.vincentf13.service.spot.matching.engine;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import static open.vincentf13.service.spot.infra.Constants.Asset;

/**
 系統指令處理器 (System Processor)
 職責：處理身份認證、資產初始化等非交易撮合指令
 */
@Slf4j
@Component
public class SystemProcessor {
    private final Ledger ledger;
    private final ExecutionReporter reporter;
    
    public SystemProcessor(Ledger ledger,
                           ExecutionReporter reporter) {
        this.ledger = ledger;
        this.reporter = reporter;
    }
    
    /**
     處理用戶認證
     邏輯：初始化核心帳戶，發送成功回報 (由 reporter 負責攔截重播副作用)
     */
    public void handleAuth(long userId,
                           long gwSeq) {
        // 1. 初始化資產帳戶
        ledger.initBalance(userId, Asset.BTC, gwSeq);
        ledger.initBalance(userId, Asset.USDT, gwSeq);
        
        // 2. 發送認證成功回報
        reporter.sendAuthSuccess(userId, gwSeq);
        
        log.debug("用戶 {} 認證處理完成 (gwSeq: {})", userId, gwSeq);
    }
}
