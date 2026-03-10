package open.vincentf13.service.spot_exchange.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.openhft.chronicle.bytes.BytesMarshallable;

/** 
  指令冪等性鍵 (userId + clientOrderId)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CidKey implements BytesMarshallable {
    private long userId;
    private String cid;
}
