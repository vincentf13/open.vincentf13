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
 最佳化：採用 AeronUtil 的 tryClaim 機制實現原地寫入，消除所有中間拷貝
 */
@Component
public class AeronSender extends Worker {
    private final Aeron aeron;
    private Publication publication;
    private ExcerptTailer tailer;
    private final Progress progress = new Progress();
    // 每個發送執行緒獨佔一個 BufferClaim
    private final BufferClaim bufferClaim = new BufferClaim();
    // 用於零拷貝包裝 Payload 的暫時 Buffer
    private final UnsafeBuffer payloadWrapBuffer = new UnsafeBuffer(0, 0);

    public AeronSender(Aeron aeron) {
        this.aeron = aeron;
    }

    /** 初始化並啟動工作執行緒 */
    @PostConstruct public void init() { start("gw-command-sender"); }

    /** 工作啟動準備 */
    @Override
    protected void onStart() {
        publication = aeron.addPublication(Channel.INBOUND, Channel.IN_STREAM);
        tailer = Storage.self().gatewayQueue().createTailer();
        Progress saved = Storage.self().metadata().get(PK_GW_COMMAND_SENDER);
        if (saved != null) {
            progress.setLastProcessedSeq(saved.getLastProcessedSeq());
            tailer.moveToIndex(progress.getLastProcessedSeq());
        }
    }

    /** 
     核心工作循環 (Zero-Copy)：
     1. 從本地隊列讀取文檔
     2. 調用 AeronUtil 實現三階段提交發送
     3. 成功後更新進度
     */
    @Override
    protected int doWork() {
        boolean handled = tailer.readDocument(wire -> {
            long seq = tailer.index();
            int msgType = wire.read(Fields.msgType).int32();
            
            if (msgType == MSG_AUTH) {
                // 認證訊息：[msgType(4)][userId(8)]
                long userId = wire.read(Fields.userId).int64();
                AeronUtil.claimAndSend(publication, bufferClaim, 12, idleStrategy, running, (buffer, offset) -> {
                    buffer.putInt(offset, MSG_AUTH);
                    buffer.putLong(offset + 4, userId);
                });
            } else if (msgType == MSG_ORDER_CREATE) {
                // 交易指令：[msgType(4)][SBE_Payload(N)] (零拷貝)
                wire.read(Fields.payload).bytes(payload -> {
                    int payloadLength = (int) payload.readRemaining();
                    AeronUtil.claimAndSend(publication, bufferClaim, 4 + payloadLength, idleStrategy, running, (buffer, offset) -> {
                        buffer.putInt(offset, MSG_ORDER_CREATE);
                        // 零拷貝核心：將 Chronicle 內存位址包裝後 拷貝至 Aeron 緩衝區
                        payloadWrapBuffer.wrap(payload.addressForRead(payload.readPosition()), payloadLength);
                        buffer.putBytes(offset + 4, payloadWrapBuffer, 0, payloadLength);
                    });
                });
            }
            progress.setLastProcessedSeq(seq);
            Storage.self().metadata().put(PK_GW_COMMAND_SENDER, progress);
        });
        return handled ? 1 : 0;
    }

    /** 資源清理 */
    @Override
    protected void onStop() { 
        if (publication != null) publication.close();
    }
}
