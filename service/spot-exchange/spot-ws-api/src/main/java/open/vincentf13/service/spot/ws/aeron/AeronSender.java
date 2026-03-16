package open.vincentf13.service.spot.ws.aeron;

import io.aeron.Aeron;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.wire.WireIn;
import open.vincentf13.service.spot.infra.aeron.AbstractAeronSender;
import open.vincentf13.service.spot.infra.alloc.ThreadContext;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.infra.alloc.OffHeapUtil;
import open.vincentf13.service.spot.model.command.*;
import org.springframework.stereotype.Component;

import static open.vincentf13.service.spot.infra.Constants.*;
import static org.agrona.UnsafeAccess.UNSAFE;

/** 
 網關 Aeron 發送器 (Raw WAL 讀取版)
 */
@Slf4j
@Component
public class AeronSender extends AbstractAeronSender {

    public AeronSender(Aeron aeron) {
        super(aeron, Storage.self().gatewaySenderWal(), 
              AeronChannel.MATCHING_FLOW, AeronChannel.DATA_STREAM_ID,
              AeronChannel.REPORT_FLOW, AeronChannel.CONTROL_STREAM_ID);
    }

    @PostConstruct public void init() { start("gw-command-sender"); }

    @Override
    public void onWalMessage(WireIn wire) {
        final long ctxSeq = tailer.index();
        final Bytes<?> bytes = wire.bytes();
        if (bytes.readRemaining() < 4) return;
        
        int payloadLen = bytes.readInt(); // 讀取手動寫入的長度
        if (bytes.readRemaining() < payloadLen) return;

        final long addressForRead = bytes.addressForRead(bytes.readPosition());
        final int msgType = UNSAFE.getInt(addressForRead);
        
        final ThreadContext tc = ThreadContext.get();
        
        AbstractSbeModel cmd = switch (msgType) {
            case MsgType.AUTH         -> tc.getAuthCommand();
            case MsgType.ORDER_CREATE  -> tc.getOrderCreateCommand();
            case MsgType.ORDER_CANCEL  -> tc.getOrderCancelCommand();
            case MsgType.DEPOSIT      -> tc.getDepositCommand();
            default -> null;
        };

        if (cmd != null) {
            log.debug("[GATEWAY-SENDER] 正在發送訊息至 Aeron: type={}, seq={}", msgType, ctxSeq);
            this.backPressureCount += aeronClient.send(payloadLen, (buffer, offset) -> {
                final long aeronDstAddress = OffHeapUtil.getAddress(buffer, offset);
                // 零拷貝轉移
                UNSAFE.copyMemory(addressForRead, aeronDstAddress, (long) payloadLen);
                // 覆蓋 Sequence，確保與 WAL Index 同步
                buffer.putLong(offset + AbstractSbeModel.SEQ_OFFSET, ctxSeq);
            });
            Storage.self().metricsHistory().compute(Storage.KEY_AERON_SEND_COUNT, (k, v) -> v == null ? 1L : v + 1);
            bytes.readSkip((long) payloadLen);
        }
    }
}
