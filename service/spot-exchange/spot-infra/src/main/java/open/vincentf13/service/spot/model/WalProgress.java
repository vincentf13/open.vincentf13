package open.vincentf13.service.spot.model;

import lombok.Data;
import net.openhft.chronicle.bytes.BytesIn;
import net.openhft.chronicle.bytes.BytesMarshallable;
import net.openhft.chronicle.bytes.BytesOut;

import static open.vincentf13.service.spot.infra.Constants.*;

/**
 * WAL 隊列處理進度與系統狀態 (WalProgress)
 * 職責：持久化記錄各組件對本地隊列 (Chronicle Queue) 的讀取索引，及引擎的全局計數器
 */
@Data
public class WalProgress implements BytesMarshallable {
    // 最後處理的本地隊列索引 (Physical Queue Index)
    private long lastProcessedIndex = WAL_INDEX_NONE;

    // 最後處理的消息序號 (Logical Message Sequence)
    // 專用於 Matching Engine 實現指令級冪等
    private long lastProcessedMsgSeq = MSG_SEQ_NONE;

    // 全局序號計數器
    private long orderIdCounter = 1L;
    private long tradeIdCounter = 1L;

    @Override
    public void writeMarshallable(BytesOut<?> bytes) {
        bytes.writeLong(lastProcessedIndex);
        bytes.writeLong(lastProcessedMsgSeq);
        bytes.writeLong(orderIdCounter);
        bytes.writeLong(tradeIdCounter);
    }

    @Override
    public void readMarshallable(BytesIn<?> bytes) {
        lastProcessedIndex = bytes.readLong();
        lastProcessedMsgSeq = bytes.readLong();
        orderIdCounter = bytes.readLong();
        tradeIdCounter = bytes.readLong();
    }
}
