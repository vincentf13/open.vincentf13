package open.vincentf13.service.spot.matching.aeron;

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
 Matching Core Aeron 發送器 (Zero-Copy 最佳化版)
 職責：將核心引擎產生的成交回報、行情數據等從 Result Queue 讀取並透過 Aeron 發送給 Gateway
 最佳化：利用 UnsafeBuffer.wrap 直接映射 Chronicle 堆外內存地址，消除傳輸前的數據拷貝
 */
@Component
public class AeronSender extends Worker {
    private final Aeron aeron;
    private Publication publication;
    private ExcerptTailer tailer;
    private final Progress progress = new Progress();
    // 零拷貝發送 Buffer
    private final UnsafeBuffer unsafeBuffer = new UnsafeBuffer(0, 0);

    public AeronSender(Aeron aeron) {
        this.aeron = aeron;
    }

    /** 初始化並啟動工作執行緒 */
    @PostConstruct public void init() { start("core-result-sender"); }

    /** 
     工作啟動準備：
     1. 建立 Aeron Publication (Result Stream)
     2. 建立 Chronicle Result Queue Tailer 並加載 Checkpoint
     */
    @Override
    protected void onStart() {
        publication = aeron.addPublication(Channel.OUTBOUND, Channel.OUT_STREAM);
        tailer = Storage.self().resultQueue().createTailer();
        Progress saved = Storage.self().metadata().get(PK_RESULT);
        if (saved != null) {
            progress.setLastProcessedSeq(saved.getLastProcessedSeq());
            tailer.moveToIndex(progress.getLastProcessedSeq());
        }
    }

    /** 
     核心工作循環 (Zero-Copy)：
     1. 從 Result Queue 讀取文檔
     2. 直接獲取二進制 Payload 內存地址
     3. 透過 Aeron 發布，成功後更新進度
     */
    @Override
    protected int doWork() {
        boolean handled = tailer.readDocument(wire -> {
            long seq = tailer.index();
            
            // 零拷貝讀取：直接操作 Chronicle 內存
            wire.read("payload").bytes(bytes -> {
                long address = bytes.addressForRead(bytes.readPosition());
                int length = (int) bytes.readRemaining();
                
                unsafeBuffer.wrap(address, length);
                
                // 背壓重試
                while (publication.offer(unsafeBuffer, 0, length) < 0) {
                    if (!running.get()) return;
                    idleStrategy.idle();
                }
            });
            
            // 記錄進度
            progress.setLastProcessedSeq(seq);
            Storage.self().metadata().put(PK_RESULT, progress);
        });
        return handled ? 1 : 0;
    }

    /** 資源清理 */
    @Override
    protected void onStop() { 
        if (publication != null) publication.close();
    }
}
