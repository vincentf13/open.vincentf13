package open.vincentf13.service.spot.model;

import lombok.Data;
import net.openhft.chronicle.bytes.BytesIn;
import net.openhft.chronicle.bytes.BytesMarshallable;
import net.openhft.chronicle.bytes.BytesOut;

/**
 活躍掛單數據結構 - 數值優化版
 */
@Data
public class Order implements BytesMarshallable {
    private long orderId;
    private long userId;
    private long price;
    private long qty;
    private long filled;
    private long version;
    private long timestamp;
    private long lastSeq;
    private int symbolId;
    private long clientOrderId;
    private byte side; // 0=BUY, 1=SELL
    private byte status; // 0=NEW, 1=PARTIAL, 2=FILLED, 3=CANCELED

    public void fill(long orderId, long userId, int symbolId, long price, long qty, byte side, long clientOrderId, long gwSeq) {
        this.orderId = orderId;
        this.userId = userId;
        this.symbolId = symbolId;
        this.price = price;
        this.qty = qty;
        this.filled = 0;
        this.side = side;
        this.status = 0;
        this.version = 1;
        this.lastSeq = gwSeq;
        this.clientOrderId = clientOrderId;
    }
    
    @Override
    public void writeMarshallable(BytesOut<?> bytes) {
        bytes.writeLong(orderId);
        bytes.writeLong(userId);
        bytes.writeLong(price);
        bytes.writeLong(qty);
        bytes.writeLong(filled);
        bytes.writeLong(version);
        bytes.writeLong(timestamp);
        bytes.writeLong(lastSeq);
        bytes.writeInt(symbolId);
        bytes.writeLong(clientOrderId);
        bytes.writeByte(side);
        bytes.writeByte(status);
    }
    
    @Override
    public void readMarshallable(BytesIn<?> bytes) {
        orderId = bytes.readLong();
        userId = bytes.readLong();
        price = bytes.readLong();
        qty = bytes.readLong();
        filled = bytes.readLong();
        version = bytes.readLong();
        timestamp = bytes.readLong();
        lastSeq = bytes.readLong();
        symbolId = bytes.readInt();
        clientOrderId = bytes.readLong();
        side = bytes.readByte();
        status = bytes.readByte();
    }
}
