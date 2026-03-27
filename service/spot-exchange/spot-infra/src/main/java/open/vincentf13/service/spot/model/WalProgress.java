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

    public long nextOrderId() { return orderIdCounter++; }
    public long nextTradeId() { return tradeIdCounter++; }

    public void alignNextIds(long maxDurableOrderId, long maxDurableTradeId) {
        long expectedNextOrderId = Math.max(1L, maxDurableOrderId + 1);
        long expectedNextTradeId = Math.max(1L, maxDurableTradeId + 1);
        if (orderIdCounter < expectedNextOrderId) {
            orderIdCounter = expectedNextOrderId;
        }
        if (tradeIdCounter < expectedNextTradeId) {
            tradeIdCounter = expectedNextTradeId;
        }
    }

    public void advanceLastProcessedMsgSeq(long seq) {
        if (seq == MSG_SEQ_NONE) return;
        if (lastProcessedMsgSeq != MSG_SEQ_NONE && seq != lastProcessedMsgSeq + 1) {
            throw new IllegalStateException(
                "Message sequence must advance monotonically, expected=%d, actual=%d"
                    .formatted(lastProcessedMsgSeq + 1, seq)
            );
        }
        lastProcessedMsgSeq = seq;
    }

    public void copyFrom(WalProgress other) {
        this.lastProcessedIndex = other.lastProcessedIndex;
        this.lastProcessedMsgSeq = other.lastProcessedMsgSeq;
        this.orderIdCounter = other.orderIdCounter;
        this.tradeIdCounter = other.tradeIdCounter;
    }

    public void reset() {
        this.lastProcessedIndex = WAL_INDEX_NONE;
        this.lastProcessedMsgSeq = MSG_SEQ_NONE;
        this.orderIdCounter = 1L;
        this.tradeIdCounter = 1L;
    }
}
