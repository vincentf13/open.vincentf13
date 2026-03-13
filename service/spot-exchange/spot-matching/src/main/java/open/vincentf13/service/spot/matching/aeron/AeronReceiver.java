package open.vincentf13.service.spot.matching.aeron;

import io.aeron.Aeron;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.wire.DocumentContext;
import open.vincentf13.service.spot.infra.aeron.AbstractAeronReceiver;
import open.vincentf13.service.spot.infra.alloc.ThreadContext;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.model.command.*;
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
    protected void onMessage(org.agrona.DirectBuffer buffer, int offset, int length) {
        final int msgType = buffer.getInt(offset);
        final long seq = buffer.getLong(offset + 4);
        ThreadContext ctx = ThreadContext.get();
        
        try (DocumentContext dc = wal.acquireAppender().writingDocument()) {
            dc.wire().write(ChronicleWireKey.msgType).int32(msgType);
            
            switch (msgType) {
                case MsgType.AUTH -> {
                    AuthCommand cmd = ctx.getAuthCommand();
                    cmd.setSeq(seq);
                    cmd.setUserId(buffer.getLong(offset + 12));
                    dc.wire().write(ChronicleWireKey.payload).marshallable(cmd);
                }
                case MsgType.ORDER_CREATE -> {
                    OrderCreateCommand cmd = ctx.getOrderCreateCommand();
                    cmd.setSeq(seq);
                    cmd.fillFrom(buffer, offset + 12, length - 12);
                    dc.wire().write(ChronicleWireKey.payload).marshallable(cmd);
                }
                case MsgType.ORDER_CANCEL -> {
                    OrderCancelCommand cmd = ctx.getOrderCancelCommand();
                    cmd.setSeq(seq);
                    cmd.setUserId(buffer.getLong(offset + 12));
                    cmd.setOrderId(buffer.getLong(offset + 20));
                    dc.wire().write(ChronicleWireKey.payload).marshallable(cmd);
                }
                case MsgType.DEPOSIT -> {
                    DepositCommand cmd = ctx.getDepositCommand();
                    cmd.setSeq(seq);
                    cmd.setUserId(buffer.getLong(offset + 12));
                    cmd.setAssetId(buffer.getInt(offset + 20));
                    cmd.setAmount(buffer.getLong(offset + 24));
                    dc.wire().write(ChronicleWireKey.payload).marshallable(cmd);
                }
                case MsgType.SNAPSHOT -> {
                    SnapshotCommand cmd = ctx.getSnapshotCommand();
                    cmd.setSeq(seq);
                    dc.wire().write(ChronicleWireKey.payload).marshallable(cmd);
                }
            }
        }
    }
}
