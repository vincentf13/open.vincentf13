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
    private long frozen; // Initially frozen amount (for BUY orders)
    private long version;
    private long timestamp;
    private long lastSeq;
    private int symbolId;
    private long clientOrderId;
    private byte side; // 0=BUY, 1=SELL
    private byte status; // 0=NEW, 1=PARTIAL, 2=FILLED, 3=CANCELED

    public void fill(long orderId, long userId, int symbolId, long price, long qty, byte side, long clientOrderId, long timestamp, long gwSeq, long frozen) {
        this.orderId = orderId;
        this.userId = userId;
        this.symbolId = symbolId;
        this.price = price;
        this.qty = qty;
        this.filled = 0;
        this.frozen = frozen;
        this.side = side;
        this.status = 0;
        this.version = 1;
        this.timestamp = timestamp;
        this.lastSeq = gwSeq;
        this.clientOrderId = clientOrderId;
    }

    public long remainingQty() {
        return qty - filled;
    }

    public boolean isTerminal() {
        return status >= 2;
    }
    
    public void copyFrom(Order other) {
        if (other == null) return;
        this.orderId = other.orderId;
        this.userId = other.userId;
        this.symbolId = other.symbolId;
        this.price = other.price;
        this.qty = other.qty;
        this.filled = other.filled;
        this.frozen = other.frozen;
        this.clientOrderId = other.clientOrderId;
        this.timestamp = other.timestamp;
        this.lastSeq = other.lastSeq;
        this.side = other.side;
        this.status = other.status;
        this.version = other.version;
    }

    @Override
    public void writeMarshallable(BytesOut bytes) {
        bytes.writeLong(orderId);
        bytes.writeLong(userId);
        bytes.writeLong(price);
        bytes.writeLong(qty);
        bytes.writeLong(filled);
        bytes.writeLong(frozen);
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
        frozen = bytes.readLong();
        version = bytes.readLong();
        timestamp = bytes.readLong();
        lastSeq = bytes.readLong();
        symbolId = bytes.readInt();
        clientOrderId = bytes.readLong();
        side = bytes.readByte();
        status = bytes.readByte();
    }
}
