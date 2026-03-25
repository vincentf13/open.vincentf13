package open.vincentf13.service.spot.infra.aeron;

import io.aeron.Publication;
import io.aeron.logbuffer.BufferClaim;
import open.vincentf13.service.spot.infra.thread.Strategies;
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
     * 發送訊息 (Claim 模式) - 精簡 ThreadLocal 版
     * 職責：封裝 Aeron Publication 的 Claim 流程，減少外部入參與字段依賴。
     *
     * @param publication Aeron 發送端
     * @param length      訊息總長度
     * @param handler     訊息填充邏輯
     * @param running     運行狀態標記
     * @return 發送結果狀態碼 (0:成功, 負數:失敗)
     */
    public static int send(Publication publication, int length, AeronHandler handler, AtomicBoolean running) {
        final BufferClaim bufferClaim = AeronThreadContext.bufferClaim();
        final IdleStrategy idleStrategy = Strategies.BUSY_SPIN;
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
