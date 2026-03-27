package open.vincentf13.service.spot.infra.aeron;

import io.aeron.Publication;
import io.aeron.logbuffer.BufferClaim;
import org.agrona.MutableDirectBuffer;

/**
 * Aeron 基礎工具類 (極簡版)
 * 職責：封裝 Aeron Publication 的 Claim 流程，將複雜的 long 返回值轉化為簡潔的狀態。
 */
public class AeronUtil {
    
    // 發送結果常數定義
    public static final int SEND_OK = 0;              // 發送成功
    public static final int SEND_BACKPRESSURE = -1;    // 系統背壓 (應重試)
    public static final int SEND_DISCONNECTED = -2;    // 鏈路斷開 (應重連)
    public static final int SEND_ERROR = -3;           // 通道關閉或其它異常

    @FunctionalInterface
    public interface AeronHandler {
        void onFill(MutableDirectBuffer buffer, int offset);
    }

    /**
     * 發送訊息 (Claim 模式)
     * @return SEND_OK, SEND_BACKPRESSURE, SEND_DISCONNECTED, SEND_ERROR
     */
    public static int send(Publication pub, int len, AeronHandler handler) {
        final BufferClaim claim = AeronThreadContext.bufferClaim();
        final long res = pub.tryClaim(len, claim);
        
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
        return SEND_ERROR; // CLOSED 或 MAX_POSITION_EXCEEDED
    }
}
