package open.vincentf13.service.spot.matching.aeron;

import io.aeron.Aeron;
import io.aeron.Publication;
import io.aeron.logbuffer.BufferClaim;
import jakarta.annotation.PostConstruct;
import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.queue.ExcerptTailer;
import org.agrona.concurrent.UnsafeBuffer;
import org.springframework.stereotype.Component;
import open.vincentf13.service.spot.infra.Worker;
import open.vincentf13.service.spot.infra.aeron.AeronUtil;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.model.Progress;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 Matching Core Aeron 發送器 (極致 Zero-Copy 版)
 職責：將成交回報與行情數據發送至 Gateway
 最佳化：採用 AeronUtil 實現原地寫入，確保回報傳輸的極低延遲
 */
@Component
public class AeronSender extends Worker {
    private final Aeron aeron;
    private Publication publication;
    private ExcerptTailer tailer;
    private final Progress progress = new Progress();
    private final BufferClaim bufferClaim = new BufferClaim();
    // 用於零拷貝包裝 Payload 的暫時 Buffer
    private final UnsafeBuffer payloadWrapBuffer = new UnsafeBuffer(0, 0);

    public AeronSender(Aeron aeron) {
        this.aeron = aeron;
    }

    /** 初始化並啟動工作執行緒 */
    @PostConstruct public void init() { start("core-result-sender"); }

    /** 工作啟動準備 */
    @Override
    protected void onStart() {
        publication = aeron.addPublication(Channel.OUTBOUND, Channel.OUT_STREAM);
        tailer = Storage.self().resultQueue().createTailer();
        Progress saved = Storage.self().metadata().get(PK_CORE_RESULT_SENDER);
        if (saved != null) {
            progress.setLastProcessedSeq(saved.getLastProcessedSeq());
            tailer.moveToIndex(progress.getLastProcessedSeq());
        }
    }

    /** 
     核心工作循環 (Zero-Copy)：
     1. 從 Result Queue 讀取數據
     2. 透過 AeronUtil 直接寫入發送緩衝區
     3. 更新發送進度
     */
    @Override
    protected int doWork() {
        boolean handled = tailer.readDocument(wire -> {
            long seq = tailer.index();
            int msgType = wire.read("msgType").int32();
            
            // 從 Result Queue 讀取二進制 Payload 並零拷貝發送
            wire.read("payload").bytes(payload -> {
                int payloadLength = (int) payload.readRemaining();
                // 使用 infra 提供的零拷貝工具發送 [msgType(4)][Payload(N)]
                AeronUtil.claimAndSend(publication, bufferClaim, 4 + payloadLength, idleStrategy, running, (buffer, offset) -> {
                    buffer.putInt(offset, msgType);
                    // 零拷貝核心：將 Chronicle 內存位址包裝後 拷貝至 Aeron 緩衝區
                    payloadWrapBuffer.wrap(payload.addressForRead(payload.readPosition()), payloadLength);
                    buffer.putBytes(offset + 4, payloadWrapBuffer, 0, payloadLength);
                });
            });
            
            progress.setLastProcessedSeq(seq);
            Storage.self().metadata().put(PK_CORE_RESULT_SENDER, progress);
        });
        return handled ? 1 : 0;
    }

    /** 資源清理 */
    @Override
    protected void onStop() { 
        if (publication != null) publication.close();
    }
}
