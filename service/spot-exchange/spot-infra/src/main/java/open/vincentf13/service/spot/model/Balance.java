package open.vincentf13.service.spot.model;

import lombok.Data;
import net.openhft.chronicle.bytes.BytesIn;
import net.openhft.chronicle.bytes.BytesMarshallable;
import net.openhft.chronicle.bytes.BytesOut;

/**
 用戶資產餘額數據結構
 */
@Data
public class Balance implements BytesMarshallable {
    private long available;
    private long frozen;
    private long version;
    private long lastSeq; // 最後更新此餘額的 WAL Sequence ID
    private long lastTradeId; // 最後更新此餘額的全域成交 ID
    
    @Override
    public void writeMarshallable(BytesOut<?> bytes) {
        bytes.writeLong(available);
        bytes.writeLong(frozen);
        bytes.writeLong(version);
        bytes.writeLong(lastSeq);
        bytes.writeLong(lastTradeId);
    }
    
    @Override
    public void readMarshallable(BytesIn<?> bytes) {
        available = bytes.readLong();
        frozen = bytes.readLong();
        version = bytes.readLong();
        lastSeq = bytes.readLong();
        lastTradeId = bytes.readLong();
    }

    public void copyFrom(Balance other) {
        if (other == null) return;
        this.available = other.available;
        this.frozen = other.frozen;
        this.version = other.version;
        this.lastSeq = other.lastSeq;
        this.lastTradeId = other.lastTradeId;
    }
}
