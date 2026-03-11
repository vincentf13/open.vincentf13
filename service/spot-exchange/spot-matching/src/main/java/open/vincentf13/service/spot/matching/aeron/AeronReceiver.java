package open.vincentf13.service.spot.matching.aeron;

import io.aeron.Aeron;
import io.aeron.Subscription;
import io.aeron.logbuffer.FragmentHandler;
import jakarta.annotation.PostConstruct;
import open.vincentf13.service.spot.infra.Worker;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.model.Progress;
import org.springframework.stereotype.Component;
import net.openhft.chronicle.bytes.PointerBytesStore;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 Matching Core Aeron 接收器 (Zero-Copy 最佳化版)
 職責：監聽來自 Gateway 的指令數據，將其持久化至本地 Command WAL
 最佳化：從封包提取 gwSeq 並以此作為 Checkpoint 位點，消除 Aeron 序號飄移風險
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
    @PostConstruct public void init() { start("core-command-receiver"); }

    /** 
     工作啟動準備：
     1. 建立 Aeron Subscription (Inbound Stream)
     2. 加載上次處理成功的 GW Sequence ID，確保訊息不漏不重
     */
    @Override
    protected void onStart() {
        subscription = aeron.addSubscription(AeronChannel.INBOUND, AeronChannel.IN_STREAM);
        // 加載處理進度 (以 Gateway Sequence 為準)
        Progress saved = Storage.self().metadata().get(MetaDataKey.PK_CORE_COMMAND_RECEIVER);
        if (saved != null) progress.setLastProcessedSeq(saved.getLastProcessedSeq());
        else progress.setLastProcessedSeq(-1L);

        /** 
         Aeron 片段處理回調 (Zero-Copy)：
         1. 獲取數據幀位址與類型
         2. 根據 msgType 解析認證或交易指令
         3. 零拷貝寫入本地 Command Queue
         */
        fragmentHandler = (buffer, offset, length, header) -> {
            // 前 4 字節為系統訊息類型 (MsgType.*)
            int msgType = buffer.getInt(offset);
            // 隨後 8 字節為 Gateway 原始序號 (gwSeq)
            long gwSeq = buffer.getLong(offset + 4);
            
            // 冪等性檢查：基於 GW 序號，防止 Driver 重啟導致的重複處理
            if (gwSeq <= progress.getLastProcessedSeq()) return;

            long messageAddress = buffer.addressOffset() + offset;

            Storage.self().commandQueue().acquireAppender().writeDocument(wire -> {
                wire.write("msgType").int32(msgType);
                wire.write("gwSeq").int64(gwSeq);
                
                if (msgType == MsgType.AUTH) {
                    // 認證訊息：讀取 8 字節 userId (Offset: 4+8=12)
                    long userId = buffer.getLong(offset + 12);
                    wire.write("userId").int64(userId);
                } else if (msgType == MsgType.ORDER_CREATE) {
                    // 交易指令：零拷貝寫入剩餘 Payload (Offset: 12)
                    pointerBytesStore.set(messageAddress + 12, length - 12);
                    wire.write("payload").bytes(pointerBytesStore);
                }
                wire.write("aeronSeq").int64(header.position()); 
            });

            // 更新並持久化進度 (記錄最後處理成功的 GW Seq)
            progress.setLastProcessedSeq(gwSeq);
            Storage.self().metadata().put(MetaDataKey.PK_CORE_COMMAND_RECEIVER, progress);
        };
    }

    /** 核心工作循環 */
    @Override protected int doWork() { return subscription.poll(fragmentHandler, 10); }

    /** 資源清理 */
    @Override protected void onStop() { if (subscription != null) subscription.close(); }
}
