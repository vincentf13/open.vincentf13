package open.vincentf13.service.spot.matching.aeron;

import io.aeron.Aeron;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.wire.DocumentContext;
import open.vincentf13.service.spot.infra.aeron.AbstractAeronReceiver;
import open.vincentf13.service.spot.infra.alloc.*;
import open.vincentf13.service.spot.infra.alloc.aeron.*;
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
        super(aeron, Storage.self().engineReceiverWal(), Storage.self().msgMetadata(),
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
        ThreadContext ctx = ThreadContext.get();
        
        try (DocumentContext dc = wal.acquireAppender().writingDocument()) {
            dc.wire().write(ChronicleWireKey.msgType).int32(msgType);
            
            switch (msgType) {
                case MsgType.AUTH -> {
                    AeronAuth aeron = ctx.getAeronAuth().wrap(buffer, offset, length);
                    AuthCommand cmd = ctx.getAuthCommand();
                    cmd.fillFrom(aeron);
                    dc.wire().write(ChronicleWireKey.payload).bytesMarshallable(cmd);
                }
                case MsgType.ORDER_CREATE -> {
                    AeronOrderCreate aeron = ctx.getAeronOrderCreate().wrap(buffer, offset, length);
                    OrderCreateCommand cmd = ctx.getOrderCreateCommand();
                    cmd.fillFrom(aeron);
                    dc.wire().write(ChronicleWireKey.payload).bytesMarshallable(cmd);
                }
                case MsgType.ORDER_CANCEL -> {
                    AeronOrderCancel aeron = ctx.getAeronOrderCancel().wrap(buffer, offset, length);
                    OrderCancelCommand cmd = ctx.getOrderCancelCommand();
                    cmd.fillFrom(aeron);
                    dc.wire().write(ChronicleWireKey.payload).bytesMarshallable(cmd);
                }
                case MsgType.DEPOSIT -> {
                    AeronDeposit aeron = ctx.getAeronDeposit().wrap(buffer, offset, length);
                    DepositCommand cmd = ctx.getDepositCommand();
                    cmd.fillFrom(aeron);
                    dc.wire().write(ChronicleWireKey.payload).bytesMarshallable(cmd);
                }
            }
        }
    }
}
