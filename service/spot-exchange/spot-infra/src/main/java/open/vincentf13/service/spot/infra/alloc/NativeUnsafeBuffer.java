package open.vincentf13.service.spot.infra.alloc;

import lombok.Getter;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import java.nio.ByteBuffer;

/**
 * 原生堆外內存緩衝區包裝器
 * 職責：管理一塊 Direct ByteBuffer，並提供 Agrona UnsafeBuffer 視圖
 */
public class NativeUnsafeBuffer {
    private final ByteBuffer rawBuffer;
    
    @Getter
    private final MutableDirectBuffer unsafeBuffer;

    public NativeUnsafeBuffer(int capacity) {
        this.rawBuffer = ByteBuffer.allocateDirect(capacity);
        this.unsafeBuffer = new UnsafeBuffer(rawBuffer);
    }

    /** 獲取用於寫入的 Agrona 緩衝區視圖 */
    public MutableDirectBuffer wrapForWrite() {
        return unsafeBuffer;
    }

    /** 釋放資源 (雖然依賴 GC，但提供清理口) */
    public void release() {
        // DirectBuffer 的清理通常依賴 GC 或 Cleaner
    }
}
