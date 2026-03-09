package open.vincentf13.service.spot_exchange.model;

import net.openhft.chronicle.bytes.BytesMarshallable;

/** 
  活躍掛單數據結構
 */
public class ActiveOrder implements BytesMarshallable {
    private long userId;
    private int symbolId;
    private byte side; // 0=BUY, 1=SELL
    private long price;
    private long qty;
    private long filled;
    private byte status; // 0=NEW, 1=PARTIAL, 2=FILLED, 3=CANCELED
    private long timestamp;

    public long getUserId() { return userId; }
    public void setUserId(long userId) { this.userId = userId; }

    public int getSymbolId() { return symbolId; }
    public void setSymbolId(int symbolId) { this.symbolId = symbolId; }

    public byte getSide() { return side; }
    public void setSide(byte side) { this.side = side; }

    public long getPrice() { return price; }
    public void setPrice(long price) { this.price = price; }

    public long getQty() { return qty; }
    public void setQty(long qty) { this.qty = qty; }

    public long getFilled() { return filled; }
    public void setFilled(long filled) { this.filled = filled; }

    public byte getStatus() { return status; }
    public void setStatus(byte status) { this.status = status; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
