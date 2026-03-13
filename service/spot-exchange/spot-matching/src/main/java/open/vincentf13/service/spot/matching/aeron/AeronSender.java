package open.vincentf13.service.spot.matching.aeron;

import io.aeron.Aeron;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.wire.WireIn;
import open.vincentf13.service.spot.infra.aeron.AbstractAeronSender;
import open.vincentf13.service.spot.infra.alloc.aeron.AeronEnvelope;
import open.vincentf13.service.spot.infra.alloc.NativeUnsafeBuffer;
import open.vincentf13.service.spot.infra.alloc.ThreadContext;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import org.springframework.stereotype.Component;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 Matching Core Aeron 發送器
 職責：讀取回報流並發送至 Gateway，實現熱點路徑零物件分配
 */
@Slf4j
@Component
public class AeronSender extends AbstractAeronSender {

    public AeronSender(Aeron aeron) {
        super(aeron, Storage.self().matchingToGwWal(), 
              AeronChannel.GATEWAY_URL, AeronChannel.DATA_STREAM_ID,
              AeronChannel.MATCHING_URL, AeronChannel.CONTROL_STREAM_ID);
    }

    @PostConstruct public void init() { start("core-result-sender"); }

    @Override
    protected void onStart() {
        super.onStart();
        log.info("AeronSender (Core) 啟動成功...");
    }

    /** 
      指令讀取回調：以統一格式發送回報「信封」
     */
    @Override
    public void onWalMessage(WireIn wire) {
        final int ctxMsgType = wire.read(ChronicleWireKey.msgType).int32();
        long mSeq = wire.read(ChronicleWireKey.matchingSeq).int64();
        final long ctxMatchingSeq = (mSeq == 0) ? tailer.index() : mSeq;
        
        // 提取 Body：統一使用 payload 欄位
        final ThreadContext ctx = ThreadContext.get();
        final NativeUnsafeBuffer scratchBuffer = ctx.getScratchBuffer();
        scratchBuffer.clear();
        wire.read(ChronicleWireKey.payload).bytes(scratchBuffer.bytes());
        
        final int payloadLength = (int) scratchBuffer.bytes().readRemaining();
        
        // 發送：使用通用封包打包
        this.backPressureCount += aeronClient.send(AeronEnvelope.HEADER_LENGTH + payloadLength, (buffer, offset) -> {
            ctx.getAeronEnvelope().wrap(buffer, offset).write(ctxMsgType, ctxMatchingSeq, 
                    scratchBuffer.buffer(), 0, payloadLength);
        });
    }
}
