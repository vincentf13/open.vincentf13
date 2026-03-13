package open.vincentf13.service.spot.matching.aeron;

import io.aeron.Aeron;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.wire.DocumentContext;
import open.vincentf13.service.spot.infra.aeron.AbstractAeronReceiver;
import open.vincentf13.service.spot.infra.alloc.ThreadContext;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import org.springframework.stereotype.Component;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 Matching Core Aeron 接收器
 職責：監聽網路指令並透過事務寫入落地 WAL，達成零物件分配
 */
@Slf4j
@Component
public class AeronReceiver extends AbstractAeronReceiver {

    public AeronReceiver(Aeron aeron) {
        super(aeron, Storage.self().gwToMatchingWal(), Storage.self().msgMetadata(),
              MetaDataKey.Msg.MACHING_RECEVIER_POINT, AeronChannel.MATCHING_URL, AeronChannel.DATA_STREAM_ID,
              AeronChannel.GATEWAY_URL, AeronChannel.CONTROL_STREAM_ID);
    }

    @PostConstruct public void init() { start("core-command-receiver"); }

    @Override
    protected void onStart() {
        super.onStart();
        log.info("AeronReceiver (Core) 啟動：當前進度: {}，進入握手狀態...", progress.getLastProcessedSeq());
    }

    @Override
    protected void onMessage(org.agrona.DirectBuffer buffer, int offset, int length, int msgType, long seq) {
        // 事務寫入：消除 Lambda
        try (DocumentContext dc = wal.acquireAppender().writingDocument()) {
            dc.wire().write(ChronicleWireKey.msgType).int32(msgType);
            dc.wire().write(ChronicleWireKey.gwSeq).int64(seq);
            if (msgType == MsgType.AUTH) {
                dc.wire().write(ChronicleWireKey.userId).int64(buffer.getLong(offset + 12));
            } else if (msgType == MsgType.ORDER_CREATE) {
                dc.wire().write(ChronicleWireKey.payload).bytes(ThreadContext.get().getPointerMapper().wrap(buffer, offset + 12, length - 12));
            } else if (msgType == MsgType.ORDER_CANCEL) {
                dc.wire().write(ChronicleWireKey.userId).int64(buffer.getLong(offset + 12));
                dc.wire().write(ChronicleWireKey.data).int64(buffer.getLong(offset + 20));
            } else if (msgType == MsgType.DEPOSIT) {
                dc.wire().write(ChronicleWireKey.userId).int64(buffer.getLong(offset + 12));
                dc.wire().write(ChronicleWireKey.assetId).int32(buffer.getInt(offset + 20));
                dc.wire().write(ChronicleWireKey.data).int64(buffer.getLong(offset + 24));
            }
        }
    }
}
