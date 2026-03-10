package open.vincentf13.service.spot_exchange.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.openhft.chronicle.bytes.BytesIn;
import net.openhft.chronicle.bytes.BytesMarshallable;
import net.openhft.chronicle.bytes.BytesOut;
import org.jetbrains.annotations.NotNull;

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
    public void writeMarshallable(@NotNull BytesOut<?> bytes) {
        bytes.writeLong(userId);
        bytes.writeInt(assetId);
    }

    @Override
    public void readMarshallable(@NotNull BytesIn<?> bytes) {
        userId = bytes.readLong();
        assetId = bytes.readInt();
    }
}
