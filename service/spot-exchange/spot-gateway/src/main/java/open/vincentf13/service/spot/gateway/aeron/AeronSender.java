package open.vincentf13.service.spot.gateway.aeron;

import io.aeron.Aeron;
import io.aeron.Publication;
import jakarta.annotation.PostConstruct;
import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.queue.ExcerptTailer;
import org.agrona.concurrent.UnsafeBuffer;
import org.springframework.stereotype.Component;
import open.vincentf13.service.spot.infra.Worker;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.model.Progress;

import java.nio.ByteBuffer;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 Gateway Aeron 發送器
 職責：從本地 Chronicle Queue (GW WAL) 讀取已持久化的指令，並透過 Aeron 發送至核心引擎 (Matching Core)
 這種設計保證了指令在發出前已先本地落地，符合 Deterministic 確定性重播要求
 */
@Component
public class AeronSender extends Worker {
    private final Aeron aeron;
    private Publication publication;
    private ExcerptTailer tailer;
    private final Progress progress = new Progress();
    private final UnsafeBuffer aeronBuffer = new UnsafeBuffer(0, 0);
    private final Bytes<ByteBuffer> reusableBytes = Bytes.elasticByteBuffer(1024);

    public AeronSender(Aeron aeron) {
        this.aeron = aeron;
    }

    /** 初始化並啟動工作執行緒 */
    @PostConstruct public void init() { start("gw-aeron-sender"); }

    /** 
     工作啟動準備：
     1. 建立 Aeron Publication
     2. 建立 Chronicle Queue Tailer 並跳轉至上次處理的進度 (Checkpoint)
     */
    @Override
    protected void onStart() {
        publication = aeron.addPublication(Channel.INBOUND, Channel.IN_STREAM);
        tailer = Storage.self().gatewayQueue().createTailer();
        Progress saved = Storage.self().metadata().get(PK_GATEWAY_IN);
        if (saved != null) {
            progress.setLastProcessedSeq(saved.getLastProcessedSeq());
            tailer.moveToIndex(progress.getLastProcessedSeq());
        }
    }

    /** 
     核心工作循環：
     1. 從本地隊列讀取文檔
     2. 提取二進制 Payload (SBE 編碼數據)
     3. 透過 Aeron 發布至通訊頻道，若遇到背壓則進入 Idle 重試
     4. 成功發送後更新並保存處理進度
     */
    @Override
    protected int doWork() {
        boolean handled = tailer.readDocument(wire -> {
            long seq = tailer.index();
            reusableBytes.clear(); wire.read("payload").bytes(reusableBytes);
            aeronBuffer.wrap(reusableBytes.addressForRead(reusableBytes.readPosition()), (int)reusableBytes.readRemaining());
            
            // 實作背壓處理與重試邏輯：當 Aeron 緩衝區滿或暫時無法發送時，持續重試
            while (publication.offer(aeronBuffer, 0, aeronBuffer.capacity()) < 0) {
                if (!running.get()) return;
                idleStrategy.idle();
            }
            // 更新並持久化當前已處理的 Sequence ID
            progress.setLastProcessedSeq(seq);
            Storage.self().metadata().put(PK_GATEWAY_IN, progress);
        });
        return handled ? 1 : 0;
    }

    /** 資源清理：關閉 Publication 並釋放堆外內存 */
    @Override
    protected void onStop() { 
        if (publication != null) publication.close();
        reusableBytes.releaseLast(); 
    }
}
