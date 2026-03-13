package open.vincentf13.service.spot.matching.engine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 系統指令處理器 (AuthProcessor)
 職責：處理身份認證、資產初始化等非交易撮合指令
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthProcessor {
    private final Ledger ledger;
    private final ExecutionReporter reporter;

    /** 
      處理用戶認證 
     */
    public void handleAuth(long userId, long gwSeq) {
        // 1. 初始化核心資產帳戶 (擴展支援動態幣對，預先初始化 1-10 號資產)
        for (int i = 1; i <= 10; i++) {
            ledger.initAccount(userId, i, gwSeq);
        }
        
        // 2. 發送認證成功回報
        reporter.reportAuth(userId, gwSeq);
        
        log.debug("用戶 {} 認證處理完成 (gwSeq: {})", userId, gwSeq);
    }
}
