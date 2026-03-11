package open.vincentf13.service.spot.gateway.aeron;

import io.aeron.Aeron;
import io.aeron.Publication;
import io.aeron.logbuffer.BufferClaim;
import jakarta.annotation.PostConstruct;
import net.openhft.chronicle.queue.ExcerptTailer;
import open.vincentf13.service.spot.infra.Worker;
import open.vincentf13.service.spot.infra.aeron.AeronUtil;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.model.Progress;
import org.agrona.concurrent.UnsafeBuffer;
import org.springframework.stereotype.Component;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 Gateway Aeron 發送器 (極致 Zero-Copy 版)
 職責：從 GW WAL 讀取指令並透過 Aeron 發送至核心引擎
 最佳化：在封包中封裝 GW 原始 Index，確保全系統確定性
 */
@Component
public class AeronSender extends Worker {
    private final Aeron aeron;
    private Publication publication;
    private ExcerptTailer tailer;
    private final Progress progress = new Progress();
    private final BufferClaim bufferClaim = new BufferClaim();
    private final UnsafeBuffer payloadWrapBuffer = new UnsafeBuffer(0, 0);

    public AeronSender(Aeron aeron) {
        this.aeron = aeron;
    }

    /** 初始化並啟動工作執行緒 */
    @PostConstruct public void init() { start("gw-command-sender"); }

    /** 工作啟動準備 */
    @Override
    protected void onStart() {
        publication = aeron.addPublication(AeronChannel.INBOUND, AeronChannel.IN_STREAM);
        tailer = Storage.self().gatewayQueue().createTailer();
        Progress saved = Storage.self().metadata().get(MetaDataKey.PK_GW_COMMAND_SENDER);
        if (saved != null) {
            progress.setLastProcessedSeq(saved.getLastProcessedSeq());
            tailer.moveToIndex(progress.getLastProcessedSeq());
        }
    }

    /** 
     核心工作循環 (Zero-Copy)：
     1. 從本地隊列讀取文檔
     2. 封裝封包：[msgType(4)][gwSeq(8)][Content(N)]
     3. 透過 AeronUtil 直接寫入發送緩衝區
     */
    @Override
    protected int doWork() {
        boolean handled = tailer.readDocument(wire -> {
            long seq = tailer.index();
            int msgType = wire.read(ChronicleWireKey.msgType).int32();
            
            if (msgType == MsgType.AUTH) {
                // 認證訊息：[msgType(4)][gwSeq(8)][userId(8)]
                long userId = wire.read(ChronicleWireKey.userId).int64();
                AeronUtil.claimAndSend(publication, bufferClaim, 20, idleStrategy, running, (buffer, offset) -> {
                    buffer.putInt(offset, MsgType.AUTH);
                    buffer.putLong(offset + 4, seq);
                    buffer.putLong(offset + 12, userId);
                });
            } else if (msgType == MsgType.ORDER_CREATE) {
                // 交易指令：[msgType(4)][gwSeq(8)][SBE_Payload(N)] (零拷貝)
                wire.read(ChronicleWireKey.payload).bytes(payload -> {
                    int payloadLength = (int) payload.readRemaining();
                    AeronUtil.claimAndSend(publication, bufferClaim, 12 + payloadLength, idleStrategy, running, (buffer, offset) -> {
                        buffer.putInt(offset, MsgType.ORDER_CREATE);
                        buffer.putLong(offset + 4, seq);
                        payloadWrapBuffer.wrap(payload.addressForRead(payload.readPosition()), payloadLength);
                        buffer.putBytes(offset + 12, payloadWrapBuffer, 0, payloadLength);
                    });
                });
            }
            
            progress.setLastProcessedSeq(seq);
            Storage.self().metadata().put(MetaDataKey.PK_GW_COMMAND_SENDER, progress);
        });
        return handled ? 1 : 0;
    }

    /** 資源清理 */
    @Override
    protected void onStop() { 
        if (publication != null) publication.close();
    }
}
