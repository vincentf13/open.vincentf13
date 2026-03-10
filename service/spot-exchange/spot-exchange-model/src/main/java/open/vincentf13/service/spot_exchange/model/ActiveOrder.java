package open.vincentf13.service.spot_exchange.model;

import lombok.Data;
import net.openhft.chronicle.bytes.BytesIn;
import net.openhft.chronicle.bytes.BytesMarshallable;
import net.openhft.chronicle.bytes.BytesOut;
import org.jetbrains.annotations.NotNull;

/** 
  活躍掛單數據結構
 */
@Data
public class ActiveOrder implements BytesMarshallable {
    private long orderId;
    private long userId;
    private long price;
    private long qty;
    private long filled;
    private long version; // 樂觀鎖版本
    private long timestamp;
    private long lastSeq; // 建立/更新此訂單的 WAL Sequence ID
    private int symbolId;
    private String clientOrderId;
    private byte side; // 0=BUY, 1=SELL
    private byte status; // 0=NEW, 1=PARTIAL, 2=FILLED, 3=CANCELED

    @Override
    public void writeMarshallable(@NotNull BytesOut<?> bytes) {
        bytes.writeLong(orderId);
        bytes.writeLong(userId);
        bytes.writeLong(price);
        bytes.writeLong(qty);
        bytes.writeLong(filled);
        bytes.writeLong(version);
        bytes.writeLong(timestamp);
        bytes.writeLong(lastSeq);
        bytes.writeInt(symbolId);
        bytes.writeUtf8(clientOrderId);
        bytes.writeByte(side);
        bytes.writeByte(status);
    }

    @Override
    public void readMarshallable(@NotNull BytesIn<?> bytes) {
        orderId = bytes.readLong();
        userId = bytes.readLong();
        price = bytes.readLong();
        qty = bytes.readLong();
        filled = bytes.readLong();
        version = bytes.readLong();
        timestamp = bytes.readLong();
        lastSeq = bytes.readLong();
        symbolId = bytes.readInt();
        clientOrderId = bytes.readUtf8();
        side = bytes.readByte();
        status = bytes.readByte();
    }
}
