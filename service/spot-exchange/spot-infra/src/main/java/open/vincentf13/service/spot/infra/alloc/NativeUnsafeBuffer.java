package open.vincentf13.service.spot.infra.alloc;

import net.openhft.chronicle.bytes.Bytes;
import org.agrona.concurrent.UnsafeBuffer;
import java.nio.ByteBuffer;

/**
 * 原生堆外非安全緩衝區 (Native Off-heap Unsafe Buffer)
 * 
 * <p>【性能優化說明】</p>
 * <ul>
 *   <li>1. <b>堆外內存 (Native/Off-heap)</b>：基於 {@link java.nio.ByteBuffer#allocateDirect(int)}，
 *          記憶體分配在 JVM 堆外，減少 GC 停頓壓力，並支援 OS 層級的零拷貝 (Zero-copy) 傳輸。</li>
 *   <li>2. <b>非安全操作 (Unsafe)</b>：底層封裝 Agrona {@link UnsafeBuffer}，透過 {@code sun.misc.Unsafe} 
 *          繞過 Java 標準的陣列邊界檢查 (Bounds Checking)，獲取極致的讀寫效能。</li>
 *   <li>3. <b>零分配 (Zero-GC)</b>：設計用於熱點路徑的物件復用，配合 {@link ThreadContext} 達成運行時零垃圾產生。</li>
 * </ul>
 * 
 * <p>【設計說明】</p>
 * {@code NativeUnsafeBuffer} 擁有並管理自己的堆外內存空間；
 * 配合 {@link ThreadContext} 達成運行時零垃圾產生。
 */
public class NativeUnsafeBuffer {
    private final Bytes<ByteBuffer> bytes;
    private final UnsafeBuffer buffer;

    public NativeUnsafeBuffer(int initialCapacity) {
        // 使用 Elastic Direct ByteBuffer 確保堆外內存語義
        this.bytes = Bytes.elasticByteBuffer(initialCapacity);
        this.buffer = new UnsafeBuffer(0, 0);
    }

    public Bytes<ByteBuffer> bytes() { return bytes; }
    public UnsafeBuffer buffer() { return buffer; }

    public void clear() {
        bytes.clear();
    }

    /** 準備寫入：自動重置位點並將 UnsafeBuffer 包裝在堆外內存的寫入位址 */
    public UnsafeBuffer wrapForWrite() {
        bytes.clear(); // 整合：自動重置位點，確保從頭寫入
        buffer.wrap(bytes.addressForWrite(0), (int) bytes.realCapacity());
        return buffer;
    }

    /** 準備讀取：將 UnsafeBuffer 包裝在堆外內存當前的讀取位置與剩餘長度 */
    public UnsafeBuffer wrapForRead() {
        buffer.wrap(bytes.addressForRead(bytes.readPosition()), (int) bytes.readRemaining());
        return buffer;
    }

    /** 手動包裝指定的堆外內存位址與長度 */
    public UnsafeBuffer wrap(long address, int length) {
        buffer.wrap(address, length);
        return buffer;
    }

    /** 將 Chronicle 的 PointerBytesStore 包裝為 Agrona Buffer (不發生拷貝) */
    public UnsafeBuffer wrap(net.openhft.chronicle.bytes.PointerBytesStore store) {
        buffer.wrap(store.addressForRead(0), (int) store.readRemaining());
        return buffer;
    }

    /** 釋放堆外內存資源 (防止 Direct Memory 洩漏) */
    public void release() {
        bytes.releaseLast();
    }
}
