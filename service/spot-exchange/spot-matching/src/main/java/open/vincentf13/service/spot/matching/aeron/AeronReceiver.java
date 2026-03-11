package open.vincentf13.service.spot.matching.aeron;

import io.aeron.Aeron;
import io.aeron.Subscription;
import io.aeron.logbuffer.FragmentHandler;
import jakarta.annotation.PostConstruct;
import open.vincentf13.service.spot.infra.Worker;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import org.springframework.stereotype.Component;
import net.openhft.chronicle.bytes.PointerBytesStore;

import static open.vincentf13.service.spot.infra.Constants.*;

import open.vincentf13.service.spot.infra.Worker;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.model.Progress;
import org.springframework.stereotype.Component;
import net.openhft.chronicle.bytes.PointerBytesStore;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 Matching Core Aeron 接收器 (Zero-Copy 最佳化版)
 職責：監聽來自 Gateway 的指令數據，將其持久化至本地 Command WAL
 最佳化：利用 PointerBytesStore 將 Aeron 接收 Buffer 直接送入 Chronicle 寫入流程，實現零拷貝落地
 */
@Component
public class AeronReceiver extends Worker {
    private final Aeron aeron;
    private Subscription subscription;
    private FragmentHandler fragmentHandler;
    private final Progress progress = new Progress();
    // 零拷貝接收 Pointer
    private final PointerBytesStore pointerBytesStore = new PointerBytesStore();

    public AeronReceiver(Aeron aeron) {
        this.aeron = aeron;
    }

    /** 初始化並啟動工作執行緒 */
    @PostConstruct public void init() { start("core-aeron-receiver"); }

    /** 
     工作啟動準備：
     1. 建立 Aeron Subscription (Inbound Stream)
     2. 加載上次處理成功的 Aeron Position，確保訊息不漏不重
     */
    @Override
    protected void onStart() {
        subscription = aeron.addSubscription(Channel.INBOUND, Channel.IN_STREAM);
        // 加載處理進度
        Progress saved = Storage.self().metadata().get(PK_COMMAND);
        if (saved != null) progress.setLastProcessedSeq(saved.getLastProcessedSeq());
        else progress.setLastProcessedSeq(-1L);

        /** 
         Aeron 片段處理回調 (Zero-Copy)：
         1. 獲取數據幀位址
         2. 零拷貝寫入本地 Command Queue
         */
        fragmentHandler = (buffer, offset, length, header) -> {
            long currentSeq = header.position();
            // 冪等性檢查
            if (currentSeq <= progress.getLastProcessedSeq()) return;

            // 零拷貝實現：直接將指針映射至 Aeron 堆外內存
            pointerBytesStore.set(buffer.addressOffset() + offset, length);
            
            // 將指令持久化至本地隊列 (Command WAL)
            Storage.self().commandQueue().acquireAppender().writeDocument(wire -> {
                // 為了簡化，目前假設 Inbound 都是 OrderCreate 類型
                wire.write("msgType").int32(100); 
                wire.write("payload").bytes(pointerBytesStore);
                wire.write("aeronSeq").int64(currentSeq); 
            });

            // 更新並持久化進度
            progress.setLastProcessedSeq(currentSeq);
            Storage.self().metadata().put(PK_COMMAND, progress);
        };
    }

    /** 核心工作循環 */
    @Override protected int doWork() { return subscription.poll(fragmentHandler, 10); }

    /** 資源清理 */
    @Override protected void onStop() { if (subscription != null) subscription.close(); }
}
