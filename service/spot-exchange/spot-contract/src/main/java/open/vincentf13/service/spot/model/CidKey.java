package open.vincentf13.service.spot.model;

import lombok.Data;
import net.openhft.chronicle.bytes.BytesIn;
import net.openhft.chronicle.bytes.BytesMarshallable;
import net.openhft.chronicle.bytes.BytesOut;

import java.util.Arrays;

/** 
 訂單冪等鍵 (CidKey) - Zero-GC 優化版
 職責：作為 Chronicle Map 的 Key，使用位元組數組代替 String 避免分配開銷
 */
@Data
public class CidKey implements BytesMarshallable {
    private long userId;
    /** 固定長度 32 位元組，對應 SBE Schema 定義 */
    private final byte[] clientOrderId = new byte[32];

    public CidKey() {}

    public CidKey(long userId, byte[] cid) {
        this.userId = userId;
        System.arraycopy(cid, 0, this.clientOrderId, 0, Math.min(cid.length, 32));
    }

    /** 零分配設置方法 */
    public void set(long userId, byte[] src, int offset, int length) {
        this.userId = userId;
        // 清理舊數據
        Arrays.fill(this.clientOrderId, (byte) 0);
        System.arraycopy(src, offset, this.clientOrderId, 0, Math.min(length, 32));
    }

    @Override
    public void writeMarshallable(BytesOut<?> bytes) {
        bytes.writeLong(userId);
        bytes.write(clientOrderId);
    }

    @Override
    public void readMarshallable(BytesIn<?> bytes) {
        userId = bytes.readLong();
        bytes.read(clientOrderId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CidKey cidKey = (CidKey) o;
        return userId == cidKey.userId && Arrays.equals(clientOrderId, cidKey.clientOrderId);
    }

    @Override
    public int hashCode() {
        int result = Long.hashCode(userId);
        result = 31 * result + Arrays.hashCode(clientOrderId);
        return result;
    }
}
