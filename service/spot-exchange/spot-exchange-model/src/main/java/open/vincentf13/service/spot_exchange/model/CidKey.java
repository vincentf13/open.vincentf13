package open.vincentf13.service.spot_exchange.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.openhft.chronicle.bytes.BytesIn;
import net.openhft.chronicle.bytes.BytesMarshallable;
import net.openhft.chronicle.bytes.BytesOut;
import org.jetbrains.annotations.NotNull;

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
    public void writeMarshallable(@NotNull BytesOut<?> bytes) {
        bytes.writeLong(userId);
        bytes.writeUtf8(cid);
    }

    @Override
    public void readMarshallable(@NotNull BytesIn<?> bytes) {
        userId = bytes.readLong();
        cid = bytes.readUtf8();
    }
}
