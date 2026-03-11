package open.vincentf13.service.spot.gateway.aeron;

import io.aeron.Aeron;
import io.aeron.Subscription;
import io.aeron.logbuffer.FragmentHandler;
import jakarta.annotation.PostConstruct;
import net.openhft.chronicle.bytes.PointerBytesStore;
import org.springframework.stereotype.Component;
import open.vincentf13.service.spot.infra.Worker;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.model.Progress;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 Gateway Aeron 接收器 (Zero-Copy 最佳化版)
 職責：監聽來自核心引擎的回報數據，並將其持久化至本地 Result WAL
 最佳化：利用 PointerBytesStore 將 Aeron 緩衝區位址直接映射給 Chronicle 寫入，消除堆內拷貝
 */
@Component
public class AeronReceiver extends Worker {
    private final Aeron aeron;
    private Subscription subscription;
    private FragmentHandler fragmentHandler;
    private final Progress progress = new Progress();
    
    // 零拷貝關鍵：指標型 Bytes 存儲，用於動態映射外部內存位址
    private final PointerBytesStore pointerBytesStore = new PointerBytesStore();

    public AeronReceiver(Aeron aeron) {
        this.aeron = aeron;
    }

    /** 初始化並啟動工作執行緒 */
    @PostConstruct public void init() { start("gw-result-receiver"); }

    /** 
     工作啟動準備：
     1. 建立 Aeron Subscription 
     2. 加載上次處理成功的 Aeron Position (Checkpoint)
     */
    @Override
    protected void onStart() {
        subscription = aeron.addSubscription(AeronChannel.OUTBOUND, AeronChannel.OUT_STREAM);
        // 加載處理進度：讀取最後一次成功處理並落地到 Result WAL 的 Aeron 位置
        Progress saved = Storage.self().metadata().get(MetaDataKey.PK_GW_RESULT_RECEIVER);
        if (saved != null) progress.setLastProcessedSeq(saved.getLastProcessedSeq());
        else progress.setLastProcessedSeq(-1L);

        /** 
         Aeron 片段處理回調 (Zero-Copy)：
         1. 獲取當前數據幀的內存位址
         2. 將 pointerBytesStore 指向該位址
         3. 讓 Chronicle 直接從該位址讀取數據並寫入文件
         */
        fragmentHandler = (buffer, offset, length, header) -> {
            long currentSeq = header.position();
            // 冪等性檢查
            if (currentSeq <= progress.getLastProcessedSeq()) return;

            // 計算當前訊息在堆外內存中的絕對物理位址
            long messageAddress = buffer.addressOffset() + offset;
            
            // 零拷貝實現：將指標指向 Aeron Buffer 的堆外地址
            pointerBytesStore.set(messageAddress, length);
            
            // 將數據原子地寫入本地 Result 隊列
            Storage.self().resultQueue().acquireAppender().writeDocument(wire -> {
                wire.bytes().write(pointerBytesStore);
                wire.write(ChronicleWireKey.aeronSeq).int64(currentSeq);
            });

            // 更新進度
            progress.setLastProcessedSeq(currentSeq);
            Storage.self().metadata().put(MetaDataKey.PK_GW_RESULT_RECEIVER, progress);
        };
    }

    /** 核心工作循環 */
    @Override protected int doWork() { return subscription.poll(fragmentHandler, 10); }

    /** 資源清理 */
    @Override protected void onStop() { if (subscription != null) subscription.close(); }
}
