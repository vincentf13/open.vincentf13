package open.vincentf13.service.spot_exchange.model;

import lombok.Data;
import net.openhft.chronicle.bytes.BytesIn;
import net.openhft.chronicle.bytes.BytesMarshallable;
import net.openhft.chronicle.bytes.BytesOut;
import org.jetbrains.annotations.NotNull;

/** 
  系統全域進度快照 (確保序號與計數器的原子對齊)
 */
@Data
public class SystemProgress implements BytesMarshallable {
    private long lastProcessedSeq; // 最後處理的 WAL 序號
    private long orderIdCounter;   // 訂單 ID 計數器
    private long tradeIdCounter;   // 成交 ID 計數器
    private long lastAeronPos;     // 最後接收到的 Aeron Position

    @Override
    public void writeMarshallable(@NotNull BytesOut<?> bytes) {
        bytes.writeLong(lastProcessedSeq);
        bytes.writeLong(orderIdCounter);
        bytes.writeLong(tradeIdCounter);
        bytes.writeLong(lastAeronPos);
    }

    @Override
    public void readMarshallable(@NotNull BytesIn<?> bytes) {
        lastProcessedSeq = bytes.readLong();
        orderIdCounter = bytes.readLong();
        tradeIdCounter = bytes.readLong();
        lastAeronPos = bytes.readLong();
    }
}
