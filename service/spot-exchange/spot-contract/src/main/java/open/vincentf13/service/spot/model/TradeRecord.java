package open.vincentf13.service.spot.model;

import lombok.Data;
import net.openhft.chronicle.bytes.BytesIn;
import net.openhft.chronicle.bytes.BytesMarshallable;
import net.openhft.chronicle.bytes.BytesOut;

/** 
  成交記錄數據結構 (冪等加固版)
 */
@Data
public class TradeRecord implements BytesMarshallable {
    private long tradeId;
    private long orderId;
    private long price;
    private long qty;
    private long time;
    private long lastSeq; // 生成此成交記錄的 WAL 序號

    @Override
    public void writeMarshallable(BytesOut<?> bytes) {
        bytes.writeLong(tradeId);
        bytes.writeLong(orderId);
        bytes.writeLong(price);
        bytes.writeLong(qty);
        bytes.writeLong(time);
        bytes.writeLong(lastSeq);
    }

    @Override
    public void readMarshallable(BytesIn<?> bytes) {
        tradeId = bytes.readLong();
        orderId = bytes.readLong();
        price = bytes.readLong();
        qty = bytes.readLong();
        time = bytes.readLong();
        lastSeq = bytes.readLong();
    }
}
