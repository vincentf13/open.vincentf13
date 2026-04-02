package open.vincentf13.service.spot.ws.aeron;

import io.aeron.Aeron;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.logbuffer.FragmentHandler;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.wire.WireIn;
import open.vincentf13.service.spot.infra.aeron.*;
import open.vincentf13.service.spot.infra.aeron.AeronConstants.AeronState;
import open.vincentf13.service.spot.infra.alloc.OffHeapUtil;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.infra.metrics.StaticMetricsHolder;
import open.vincentf13.service.spot.infra.thread.Worker;
import open.vincentf13.service.spot.model.command.AbstractSbeModel;
import org.springframework.stereotype.Component;

import static open.vincentf13.service.spot.infra.Constants.*;
import static open.vincentf13.service.spot.infra.aeron.AeronUtil.*;
import static org.agrona.UnsafeAccess.UNSAFE;

/** 網關 Aeron 發送器 (極簡扁平化版) */
@Slf4j
@Component
public class AeronSender extends Worker {
    private final ChronicleQueue wal;
    private Publication publication;
    private Subscription controlSub;
    private ExcerptTailer tailer;
    private AeronState currentState = AeronState.WAITING;
    private long localBackPressure = 0;

    public AeronSender(Aeron aeron) {
        super("gw-sender", MetricsKey.CPU_ID_AERON_SENDER, MetricsKey.CPU_ID_CURRENT_AERON_SENDER, MetricsKey.GATEWAY_AERON_SENDER_WORKER_DUTY_CYCLE);
        this.wal = Storage.self().gatewaySenderWal();
    }

    @PostConstruct @Override public void start() { super.start(); }
    @PreDestroy @Override public void stop() { super.stop(); }

    @Override
    protected void onStart() {
        this.publication = AeronClientHolder.aeron().addPublication(AeronChannel.MATCHING_FLOW, AeronChannel.DATA_STREAM_ID);
        this.controlSub = AeronClientHolder.aeron().addSubscription(AeronChannel.REPORT_FLOW, AeronChannel.CONTROL_STREAM_ID);
        this.tailer = wal.createTailer();
    }

    @Override
    protected int doWork() {
        // 1. 鏈路狀態檢查 (發送端需偵測連線是否中斷)
        if (currentState == AeronState.SENDING && !publication.isConnected()) currentState = AeronState.WAITING;

        // 2. 處理握手 (Resume)
        int work = controlSub.poll(resumeHandler, AeronConstants.AERON_POLL_LIMIT);
        if (currentState == AeronState.WAITING) return work;

        // 3. 轉發 WAL 消息 (從 Chronicle Queue 讀取並透過 Aeron 發送)
        for (int i = 0; i < AeronConstants.WAL_BATCH_SIZE; i++) {
            // 先獲取當前消息的 WAL 索引位點 (用於恢復鏈路時告知接收端)
            long seq = tailer.index();
            
            // 開啟讀取文檔 (Excerpt)，使用 try-with-resources 確保讀取完後自動釋放檔案鎖/資源
            try (var dc = tailer.readingDocument()) {
                // 若無新數據，或發送失敗 (例如連線中斷)，則結束本批次處理
                if (!dc.isPresent() || !trySend(dc.wire(), seq)) break; 
                
                // 發送成功，累加發送計數指標
                StaticMetricsHolder.addCounter(MetricsKey.AERON_SEND_COUNT, 1);
                work++;
            }
        }
        return work;
    }

    /**
     * 嘗試將單筆 WAL 消息發送至 Aeron 通道。
     * 職責：封裝 Unsafe 零拷貝邏輯與發送失敗後的重試/降級行為。
     */
    private boolean trySend(WireIn wire, long seq) {
        final net.openhft.chronicle.bytes.Bytes<?> bytes = wire.bytes();
        final int len = (int) bytes.readRemaining();
        if (len <= 0) return true;

        // 直接獲取 Chronicle 消息的堆外記憶體地址
        final long addr = bytes.addressForRead(bytes.readPosition());
        
        while (running.get()) {
            // 透過 Aeron Claim 模式發送
            int res = AeronUtil.send(publication, len, (buf, off) -> {
                // 使用 Unsafe 直接從 Chronicle WAL 位址拷貝數據至 Aeron 緩衝區 (Zero Copy)
                UNSAFE.copyMemory(addr, OffHeapUtil.getAddress(buf, off), len);
                // 覆寫消息內部的序號欄位 (強制偏移量 8，確保對齊)
                buf.putLong(off + 8, seq, java.nio.ByteOrder.LITTLE_ENDIAN);
            });

            // 成功：跳過已發送字節並結束重試
            if (res == SEND_OK) { 
                if (seq == 1) log.info("成功發送第一條消息 (seq=1)");
                bytes.readSkip(len); return true; 
            } 
            
            // 背壓：記錄指標，提示 CPU 執行 spin 等待，並繼續重試
            if (res == SEND_BACKPRESSURE) { 
                localBackPressure++; Thread.onSpinWait(); continue; 
            } 
            
            // 鏈路斷開：將狀態切換回 WAITING，等待接收端重新 Resume
            if (res == SEND_DISCONNECTED) {
                log.warn("AeronSender 鏈路斷開，暫停發送。");
                currentState = AeronState.WAITING; 
            }
            break; 
        }
        return false;
    }

    private final FragmentHandler resumeHandler = (buffer, offset, length, header) -> {
        if (buffer.getInt(offset, java.nio.ByteOrder.LITTLE_ENDIAN) == MsgType.RESUME && currentState == AeronState.WAITING) {
            long walIndex = buffer.getLong(offset + AeronConstants.MSG_SEQ_OFFSET, java.nio.ByteOrder.LITTLE_ENDIAN);
            log.info("✅ AeronSender 握手成功！恢復位點: {}", walIndex);
            if (walIndex == WAL_INDEX_NONE || walIndex == MSG_SEQ_NONE || !tailer.moveToIndex(walIndex)) tailer.toStart();
            currentState = AeronState.SENDING;
        }
    };

    @Override
    protected void onMetricsReport() {
        if (localBackPressure > 0) {
            StaticMetricsHolder.addCounter(MetricsKey.AERON_BACKPRESSURE, localBackPressure);
            localBackPressure = 0;
        }
    }

    @Override
    protected void onStop() {
        if (publication != null) publication.close();
        if (controlSub != null) controlSub.close();
    }
}
