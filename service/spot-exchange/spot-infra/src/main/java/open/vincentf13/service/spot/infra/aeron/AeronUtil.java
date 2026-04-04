package open.vincentf13.service.spot.infra.aeron;

import io.aeron.Aeron;
import io.aeron.Publication;
import io.aeron.logbuffer.BufferClaim;
import org.agrona.MutableDirectBuffer;

/**
 * Aeron 基礎工具類
 *
 * 職責：
 * 1. 全域 Aeron 實例持有 (由 AeronConfig 注入)
 * 2. 線程私有 BufferClaim 管理 (消除分配開銷)
 * 3. Publication claim 模式封裝 (long → 語義狀態碼)
 */
public class AeronUtil {

    // ========== 全域 Aeron 實例 ==========

    private static Aeron SHARED_AERON;

    public static Aeron aeron() { return SHARED_AERON; }
    public static void setAeron(Aeron aeron) { SHARED_AERON = aeron; }

    // ========== 線程私有 BufferClaim ==========

    private static final ThreadLocal<BufferClaim> BUFFER_CLAIM = ThreadLocal.withInitial(BufferClaim::new);

    public static void cleanupThreadLocal() { BUFFER_CLAIM.remove(); }

    // ========== Claim 模式發送 ==========

    public static final int SEND_OK = 0;
    public static final int SEND_BACKPRESSURE = -1;
    public static final int SEND_DISCONNECTED = -2;
    public static final int SEND_ERROR = -3;

    @FunctionalInterface
    public interface AeronHandler {
        void onFill(MutableDirectBuffer buffer, int offset);
    }

    public static int send(Publication pub, int len, AeronHandler handler) {
        BufferClaim claim = BUFFER_CLAIM.get();
        long res = pub.tryClaim(len, claim);

        if (res > 0) {
            try {
                handler.onFill(claim.buffer(), claim.offset());
            } finally {
                claim.commit();
            }
            return SEND_OK;
        }

        if (res == Publication.BACK_PRESSURED || res == Publication.ADMIN_ACTION) return SEND_BACKPRESSURE;
        if (res == Publication.NOT_CONNECTED) return SEND_DISCONNECTED;
        return SEND_ERROR;
    }
}
