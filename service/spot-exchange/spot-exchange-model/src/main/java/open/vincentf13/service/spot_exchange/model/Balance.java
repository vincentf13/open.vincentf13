package open.vincentf13.service.spot_exchange.model;

import lombok.Data;
import net.openhft.chronicle.bytes.BytesMarshallable;

/** 
  用戶資產餘額數據結構
 */
@Data
public class Balance implements BytesMarshallable {
    private long available;
    private long frozen;
    private long version;
    private long lastSeq; // 最後更新此餘額的 WAL Sequence ID
}
