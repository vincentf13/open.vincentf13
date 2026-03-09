package open.vincentf13.service.spot_exchange.model;

import net.openhft.chronicle.bytes.BytesMarshallable;

/** 
  用戶資產餘額數據結構
  使用定點數儲存，數值放大 10^8 倍
 */
public class Balance implements BytesMarshallable {
    private long available;
    private long frozen;
    private long version;

    public long getAvailable() { return available; }
    public void setAvailable(long available) { this.available = available; }

    public long getFrozen() { return frozen; }
    public void setFrozen(long frozen) { this.frozen = frozen; }

    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
}
