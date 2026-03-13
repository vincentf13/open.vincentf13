package open.vincentf13.service.spot.infra.alloc;

import org.agrona.DirectBuffer;
import net.openhft.chronicle.bytes.PointerBytesStore;

/**
 * Chronicle 指針映射器 (Memory Bridge to Chronicle)
 * 
 * <p>【設計職責】</p>
 * <ul>
 *   <li>1. <b>零拷貝橋樑</b>：專門負責將外部內存 (如 Aeron 接收到的 DirectBuffer) 映射為 Chronicle 的指針。</li>
 *   <li>2. <b>地址提取</b>：封裝了 {@link PointerBytesStore} 的地址計算與設置邏輯。</li>
 * </ul>
 */
public class ChroniclePointerMapper {
    private final PointerBytesStore pointerBytesStore = new PointerBytesStore();

    public PointerBytesStore pointerBytesStore() {
        return pointerBytesStore;
    }

    /**
     * 將外部緩衝區映射為 Chronicle 的 Bytes 指標
     * @param buffer 來源緩衝區 (通常是 Aeron 接收緩衝區)
     * @param offset 起始偏移量
     * @param length 映射長度
     * @return 映射後的指針對象
     */
    public PointerBytesStore wrap(DirectBuffer buffer, int offset, int length) {
        pointerBytesStore.set(buffer.addressOffset() + offset, length);
        return pointerBytesStore;
    }
}
