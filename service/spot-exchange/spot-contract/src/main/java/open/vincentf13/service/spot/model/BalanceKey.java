package open.vincentf13.service.spot.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.openhft.chronicle.bytes.BytesIn;
import net.openhft.chronicle.bytes.BytesMarshallable;
import net.openhft.chronicle.bytes.BytesOut;

/**
 資產餘額表的複合鍵 (userId + assetId)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BalanceKey implements BytesMarshallable {
    private long userId;
    private int assetId;
    
    @Override
    public void writeMarshallable(BytesOut<?> bytes) {
        bytes.writeLong(userId);
        bytes.writeInt(assetId);
    }
    
    @Override
    public void readMarshallable(BytesIn<?> bytes) {
        userId = bytes.readLong();
        assetId = bytes.readInt();
    }
}
