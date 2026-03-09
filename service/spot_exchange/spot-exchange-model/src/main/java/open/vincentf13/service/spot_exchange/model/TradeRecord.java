package open.vincentf13.service.spot_exchange.model;

import net.openhft.chronicle.bytes.BytesMarshallable;

/** 
  成交記錄數據結構
 */
public class TradeRecord implements BytesMarshallable {
    private long tradeId;
    private long orderId;
    private long price;
    private long qty;
    private byte side;
    private long time;

    public long getTradeId() { return tradeId; }
    public void setTradeId(long tradeId) { this.tradeId = tradeId; }

    public long getOrderId() { return orderId; }
    public void setOrderId(long orderId) { this.orderId = orderId; }

    public long getPrice() { return price; }
    public void setPrice(long price) { this.price = price; }

    public long getQty() { return qty; }
    public void setQty(long qty) { this.qty = qty; }

    public byte getSide() { return side; }
    public void setSide(byte side) { this.side = side; }

    public long getTime() { return time; }
    public void setTime(long time) { this.time = time; }
}
