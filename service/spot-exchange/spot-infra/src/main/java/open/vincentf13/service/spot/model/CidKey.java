package open.vincentf13.service.spot.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.openhft.chronicle.bytes.BytesIn;
import net.openhft.chronicle.bytes.BytesMarshallable;
import net.openhft.chronicle.bytes.BytesOut;

/** 
 訂單冪等鍵 (CidKey) - 終極數值優化版
 職責：作為 Chronicle Map 的 Key，使用純 Long 組合實現零對象分配與極速比對
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CidKey implements BytesMarshallable {
    private long userId;
    private long clientOrderId;

    public void set(long userId, long clientOrderId) {
        this.userId = userId;
        this.clientOrderId = clientOrderId;
    }

    @Override
    public void writeMarshallable(BytesOut<?> bytes) {
        bytes.writeLong(userId);
        bytes.writeLong(clientOrderId);
    }

    @Override
    public void readMarshallable(BytesIn<?> bytes) {
        userId = bytes.readLong();
        clientOrderId = bytes.readLong();
    }
}
