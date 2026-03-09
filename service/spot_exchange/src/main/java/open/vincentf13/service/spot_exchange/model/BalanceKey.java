package open.vincentf13.service.spot_exchange.model;

import net.openhft.chronicle.bytes.BytesMarshallable;

/** 
  資產餘額表的複合鍵 (userId + assetId)
 */
public class BalanceKey implements BytesMarshallable {
    private long userId;
    private int assetId;

    public BalanceKey() {}

    public BalanceKey(long userId, int assetId) {
        this.userId = userId;
        this.assetId = assetId;
    }

    public long getUserId() { return userId; }
    public void setUserId(long userId) { this.userId = userId; }

    public int getAssetId() { return assetId; }
    public void setAssetId(int assetId) { this.assetId = assetId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BalanceKey that = (BalanceKey) o;
        return userId == that.userId && assetId == that.assetId;
    }

    @Override
    public int hashCode() {
        int result = (int) (userId ^ (userId >>> 32));
        result = 31 * result + assetId;
        return result;
    }
}
