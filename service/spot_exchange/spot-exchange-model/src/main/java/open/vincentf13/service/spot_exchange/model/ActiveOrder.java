package open.vincentf13.service.spot_exchange.model;

import lombok.Data;
import net.openhft.chronicle.bytes.BytesMarshallable;

/** 
  活躍掛單數據結構
 */
@Data
public class ActiveOrder implements BytesMarshallable {
    private long orderId;
    private String clientOrderId;
    private long userId;
    private int symbolId;
    private byte side; // 0=BUY, 1=SELL
    private long price;
    private long qty;
    private long filled;
    private byte status; // 0=NEW, 1=PARTIAL, 2=FILLED, 3=CANCELED
    private long timestamp;
}
