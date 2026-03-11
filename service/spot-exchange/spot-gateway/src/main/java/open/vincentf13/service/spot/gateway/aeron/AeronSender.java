package open.vincentf13.service.spot.gateway.aeron;

import io.aeron.Aeron;
import io.aeron.Publication;
import jakarta.annotation.PostConstruct;
import net.openhft.chronicle.queue.ExcerptTailer;
import open.vincentf13.service.spot.infra.Worker;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.model.Progress;
import org.agrona.concurrent.UnsafeBuffer;
import org.springframework.stereotype.Component;

import static open.vincentf13.service.spot.infra.Constants.Channel;
import static open.vincentf13.service.spot.infra.Constants.PK_GATEWAY_IN;

/** 
 Gateway Aeron 發送器 (Zero-Copy 最佳化版)
 職責：從本地 Chronicle Queue (GW WAL) 讀取已持久化的指令，並透過 Aeron 發送至核心引擎
 最佳化：直接將 Aeron 的 UnsafeBuffer 指向 Chronicle 的堆外內存地址，實現零拷貝傳輸
 */
@Component
public class AeronSender extends Worker {
    private final Aeron aeron;
    private Publication publication;
    private ExcerptTailer tailer;
    private final Progress progress = new Progress();
    // 用於發送的 Buffer，內容會動態指向 Chronicle 的內存位址
    private final UnsafeBuffer unsafeBuffer = new UnsafeBuffer(0, 0);

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
     核心工作循環 (Zero-Copy)：
     1. 從本地隊列讀取文檔
     2. 直接獲取 Chronicle 堆外內存地址與長度
     3. 透過 UnsafeBuffer.wrap 實現「零拷貝」包裝
     4. 透過 Aeron 發布，成功後更新進度
     */
    @Override
    protected int doWork() {
        boolean handled = tailer.readDocument(wire -> {
            long seq = tailer.index();
            
            // 零拷貝關鍵點：不將 bytes 拷貝出來，而是直接在閉包內操作其內存位址
            wire.read("payload").bytes(bytes -> {
                long address = bytes.addressForRead(bytes.readPosition());
                int length = (int) bytes.readRemaining();
                
                // 將發送 Buffer 直接指向 Chronicle 映射文件的內存地址
                unsafeBuffer.wrap(address, length);
                
                // 實作背壓處理與重試邏輯
                while (publication.offer(unsafeBuffer, 0, length) < 0) {
                    if (!running.get()) return;
                    idleStrategy.idle();
                }
            });
            
            // 更新並持久化進度
            progress.setLastProcessedSeq(seq);
            Storage.self().metadata().put(PK_GATEWAY_IN, progress);
        });
        return handled ? 1 : 0;
    }

    /** 資源清理 */
    @Override
    protected void onStop() { 
        if (publication != null) publication.close();
    }
}
