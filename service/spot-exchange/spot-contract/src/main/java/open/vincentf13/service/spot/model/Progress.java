package open.vincentf13.service.spot.model;

import lombok.Data;
import net.openhft.chronicle.bytes.BytesIn;
import net.openhft.chronicle.bytes.BytesMarshallable;
import net.openhft.chronicle.bytes.BytesOut;

/** 
 系統進度與狀態快照 (Progress)
 職責：持久化記錄各組件處理到的最後位點與全局序號生成器
 */
@Data
public class Progress implements BytesMarshallable {
    // 最後處理的本地隊列索引 (Local Queue Index)
    private long lastProcessedSeq = -1L;
    
    // 最後處理的來源端業務序號 (Source Gateway Sequence)
    // 這是實現全系統指令級冪等的「最終屏障」
    private long lastProcessedGwSeq = -1L;
    
    // 全局序號計數器
    private long orderIdCounter = 1L;
    private long tradeIdCounter = 1L;

    @Override
    public void writeMarshallable(BytesOut<?> bytes) {
        bytes.writeLong(lastProcessedSeq);
        bytes.writeLong(lastProcessedGwSeq);
        bytes.writeLong(orderIdCounter);
        bytes.writeLong(tradeIdCounter);
    }

    @Override
    public void readMarshallable(BytesIn<?> bytes) {
        lastProcessedSeq = bytes.readLong();
        lastProcessedGwSeq = bytes.readLong();
        orderIdCounter = bytes.readLong();
        tradeIdCounter = bytes.readLong();
    }
}
