package open.vincentf13.service.spot.infra.aeron;

import io.aeron.logbuffer.BufferClaim;

/**
 * Aeron 線程上下文 (AeronThreadContext)
 * 職責：管理線程私有的緩衝區，減少對象分配開銷。
 */
public class AeronThreadContext {

    private static final ThreadLocal<BufferClaim> BUFFER_CLAIM = ThreadLocal.withInitial(BufferClaim::new);

    /** 獲取線程私有的 BufferClaim (必須由 ThreadLocal 管理，不可跨線程複用) */
    public static BufferClaim bufferClaim() {
        return BUFFER_CLAIM.get();
    }

    /** 清理線程私有資源 */
    public static void cleanup() {
        BUFFER_CLAIM.remove();
    }
}
