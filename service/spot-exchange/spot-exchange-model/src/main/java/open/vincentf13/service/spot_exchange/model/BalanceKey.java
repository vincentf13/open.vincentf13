package open.vincentf13.service.spot_exchange.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.openhft.chronicle.bytes.BytesMarshallable;

/** 
  資產餘額表的複合鍵 (userId + assetId)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BalanceKey implements BytesMarshallable {
    private long userId;
    private int assetId;
}
