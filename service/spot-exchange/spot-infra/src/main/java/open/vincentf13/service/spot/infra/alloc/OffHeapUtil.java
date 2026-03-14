package open.vincentf13.service.spot.infra.alloc;

import org.agrona.DirectBuffer;
import static org.agrona.UnsafeAccess.UNSAFE;

/**
 * 堆外內存操作工具類 (Off-Heap Memory Utilities)
 * 職責：封裝原始地址計算與物理內存拷貝，提供語義化接口
 */
public class OffHeapUtil {

    /**
     * 獲取 DirectBuffer 在特定偏移量處的絕對物理地址
     * 
     * @param buffer 來源緩衝區
     * @param offset 偏移量
     * @return 64 位內存地址
     */
    public static long getAddress(DirectBuffer buffer, int offset) {
        return buffer.addressOffset() + offset;
    }

    /**
     * 執行零拷貝的物理內存轉移 (memcpy)
     * 
     * @param srcAddress 來源物理地址
     * @param dstAddress 目標物理地址
     * @param length     拷貝長度 (bytes)
     */
    public static void copyMemory(long srcAddress, long dstAddress, long length) {
        UNSAFE.copyMemory(srcAddress, dstAddress, length);
    }
}
