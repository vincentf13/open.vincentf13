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
        // 1. 初始化核心資產帳戶 (依照 Asset enum 遍歷，且僅在不存在時創建)
        for (Asset asset : Asset.values()) {
            if (!ledger.hasAsset(userId, asset.getId())) {
                ledger.initAccount(userId, asset.getId(), gwSeq);
            }
        }
        
        // 2. 發送認證成功回報
        reporter.reportAuth(userId);
        
        log.debug("用戶 {} 認證處理完成 (gwSeq: {})", userId, gwSeq);
    }
}
