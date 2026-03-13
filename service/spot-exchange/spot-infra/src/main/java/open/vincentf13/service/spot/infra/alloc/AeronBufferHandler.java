package open.vincentf13.service.spot.infra.alloc;

import org.agrona.DirectBuffer;
import io.aeron.logbuffer.BufferClaim;
import net.openhft.chronicle.bytes.PointerBytesStore;

/**
 * Aeron 專用通訊緩衝區處理器 (橋樑 - Communication Bridge)
 * 
 * <p>【設計職責】</p>
 * <ul>
 *   <li>1. <b>映射外部內存 (External Memory)</b>：不主動持有數據空間，而是映射 Aeron Media Driver 管理的堆外內存位址。</li>
 *   <li>2. <b>整合 Aeron 三階段提交</b>：封裝 {@code BufferClaim} 用於 {@code Publication.tryClaim()} 預留發送空間，實現原地寫入提交。</li>
 *   <li>3. <b>零拷貝對接</b>：封裝 {@code PointerBytesStore} 將 {@code Subscription} 接收到的數據直接映射給 Chronicle Queue (WAL) 寫入。</li>
 * </ul>
 * 
 * <p>【與 NativeUnsafeBuffer 的區別】</p>
 * {@code AeronBufferHandler} 是「與網路層對接的橋樑」，它映射的是由 Aeron 驅動程序管理的內存位址；
 * 而 {@link open.vincentf13.service.spot.infra.alloc.NativeUnsafeBuffer scratchBuffer} 是「內部數據處理的工作台」，它持有並管理自己的緩衝區空間。
 */
public class AeronBufferHandler {
    private final BufferClaim bufferClaim = new BufferClaim();
    private final PointerBytesStore pointerBytesStore = new PointerBytesStore();

    public BufferClaim bufferClaim() {
        return bufferClaim;
    }

    public PointerBytesStore pointerBytesStore() {
        return pointerBytesStore;
    }

    /**
     * 將接收到的 Aeron Buffer (DirectBuffer) 包裝為 Chronicle 的 Bytes 指標
     * 常用於將網路封包零拷貝寫入 Chronicle Queue (WAL)
     * @param buffer Aeron 接收緩衝區
     * @param offset 資料起始偏移量
     * @param length 資料長度
     * @return 包裝後的 PointerBytesStore
     */
    public PointerBytesStore wrap(DirectBuffer buffer, int offset, int length) {
        pointerBytesStore.set(buffer.addressOffset() + offset, length);
        return pointerBytesStore;
    }
}
