package open.vincentf13.service.spot.infra.aeron;

import io.aeron.Publication;
import io.aeron.logbuffer.BufferClaim;
import lombok.RequiredArgsConstructor;
import org.agrona.concurrent.IdleStrategy;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Aeron 發送客戶端 (Aeron Client Wrapper)
 * <p>
 * 職責：封裝 Aeron Publication 的 Claim 流程，減少調用端的入參複雜度。
 */
@RequiredArgsConstructor
public class AeronClient {
    private final Publication publication;
    private final BufferClaim bufferClaim;
    private final IdleStrategy idleStrategy;
    private final AtomicBoolean running;

    /**
     * 發送訊息 (Claim 模式)
     *
     * @param length 訊息總長度
     * @param handler 訊息填充邏輯
     * @return 遭遇背壓 (Back-pressure) 的次數
     */
    public int send(int length, AeronUtil.AeronHandler handler) {
        int backPressureCount = 0;
        while (running.get()) {
            final long result = publication.tryClaim(length, bufferClaim);
            if (result > 0) {
                try {
                    handler.onFill(bufferClaim.buffer(), bufferClaim.offset());
                } finally {
                    bufferClaim.commit();
                }
                return backPressureCount;
            } else if (result == Publication.BACK_PRESSURED || result == Publication.ADMIN_ACTION) {
                backPressureCount++;
                idleStrategy.idle();
            } else if (result == Publication.NOT_CONNECTED) {
                throw new RuntimeException("Aeron Publication not connected!");
            } else {
                idleStrategy.idle();
            }
        }
        return backPressureCount;
    }
}
