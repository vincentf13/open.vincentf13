package open.vincentf13.service.spot_exchange.model;

import lombok.Data;
import net.openhft.chronicle.bytes.BytesMarshallable;

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
}
