package open.vincentf13.service.spot.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.openhft.chronicle.bytes.BytesIn;
import net.openhft.chronicle.bytes.BytesMarshallable;
import net.openhft.chronicle.bytes.BytesOut;

/**
 指令冪等性鍵 (userId + clientOrderId)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CidKey implements BytesMarshallable {
    private long userId;
    private String cid;
    
    @Override
    public void writeMarshallable(BytesOut<?> bytes) {
        bytes.writeLong(userId);
        bytes.writeUtf8(cid);
    }
    
    @Override
    public void readMarshallable(BytesIn<?> bytes) {
        userId = bytes.readLong();
        cid = bytes.readUtf8();
    }
}
