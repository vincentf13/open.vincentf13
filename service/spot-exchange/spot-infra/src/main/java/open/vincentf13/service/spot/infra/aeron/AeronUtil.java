package open.vincentf13.service.spot.infra.aeron;

import io.aeron.Publication;
import io.aeron.logbuffer.BufferClaim;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.IdleStrategy;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Aeron 基礎工具類
 */
public class AeronUtil {

    @FunctionalInterface
    public interface AeronHandler {
        void onFill(MutableDirectBuffer buffer, int offset);
    }

    /**
     * 發送訊息 (Claim 模式) - 靜態優化版
     * 職責：封裝 Aeron Publication 的 Claim 流程，減少對象持有成本。
     *
     * @param publication Aeron 發送端
     * @param bufferClaim 複用的 Claim 緩衝區 (Thread-safe 由調用者保證)
     * @param length      訊息總長度
     * @param handler     訊息填充邏輯
     * @param running     運行狀態標記
     * @param idleStrategy 閒置策略
     * @return 發送結果狀態碼 (0:成功, 負數:失敗)
     */
    public static int send(Publication publication, BufferClaim bufferClaim, int length, 
                           AeronHandler handler, AtomicBoolean running, IdleStrategy idleStrategy) {
        int backPressureCount = 0;
        while (running.get()) {
            final long result = publication.tryClaim(length, bufferClaim);
            if (result > 0) {
                try {
                    handler.onFill(bufferClaim.buffer(), bufferClaim.offset());
                } finally {
                    bufferClaim.commit();
                }
                return 0;
            } else if (result == Publication.BACK_PRESSURED || result == Publication.ADMIN_ACTION) {
                if (++backPressureCount > AeronConstants.BACK_PRESSURE_RETRY_LIMIT) return -2;
                idleStrategy.idle();
            } else if (result == Publication.NOT_CONNECTED) {
                return -1;
            } else if (result == Publication.CLOSED || result == Publication.MAX_POSITION_EXCEEDED) {
                return -3;
            } else {
                idleStrategy.idle();
            }
        }
        return -4;
    }
}
