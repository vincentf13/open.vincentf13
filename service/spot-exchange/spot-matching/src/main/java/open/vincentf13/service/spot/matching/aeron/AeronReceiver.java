package open.vincentf13.service.spot.matching.aeron;

import io.aeron.Aeron;
import io.aeron.FragmentAssembler;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.logbuffer.BufferClaim;
import io.aeron.logbuffer.FragmentHandler;
import jakarta.annotation.PostConstruct;
import open.vincentf13.service.spot.infra.Worker;
import open.vincentf13.service.spot.infra.alloc.ThreadContext;
import open.vincentf13.service.spot.infra.aeron.AeronUtil;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.model.Progress;
import org.springframework.stereotype.Component;
import net.openhft.chronicle.map.ChronicleMap;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.wire.DocumentContext;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 Matching Core Aeron 接收器
 職責：監聽網路指令並透過事務寫入落地 WAL，達成零物件分配
 */
@Component
public class AeronReceiver extends Worker {
    private final ChronicleQueue gwToMatchingWal = Storage.self().gwToMatchingWal();
    private final ChronicleMap<Byte, Progress> metadata = Storage.self().metadata();

    private final Aeron aeron;
    private Subscription subscription;
    private Publication controlPublication;
    private final BufferClaim bufferClaim = new BufferClaim();
    private final Progress progress = new Progress();
    
    // 使用 FragmentAssembler 確保跨 MTU 訊息的完整性 (處理網路分片)
    private FragmentAssembler assembler;
    
    private AeronState currentState = AeronState.WAITING;
    private long lastResumeSentTime = 0;

    public AeronReceiver(Aeron aeron) {
        this.aeron = aeron;
    }

    @PostConstruct public void init() { start("core-command-receiver"); }

    @Override
    protected void onStart() {
        subscription = aeron.addSubscription(AeronChannel.MATCHING_URL, AeronChannel.DATA_STREAM_ID);
        controlPublication = aeron.addPublication(AeronChannel.GATEWAY_URL, AeronChannel.CONTROL_STREAM_ID);
        
        // 初始化重組器：包裝原始 handler
        this.assembler = new FragmentAssembler(fragmentHandler);
        
        Progress saved = metadata.get(MetaDataKey.MACHING_RECEVIER_POINT);
        if (saved != null) progress.setLastProcessedSeq(saved.getLastProcessedSeq());
        else progress.setLastProcessedSeq(-1L);
        
        currentState = AeronState.WAITING; 
        log.info("AeronReceiver (Core) 啟動：當前進度: {}，進入握手狀態...", progress.getLastProcessedSeq());
    }

    @Override
    protected int doWork() {
        if (currentState == AeronState.SENDING && !subscription.isConnected()) {
            currentState = AeronState.WAITING;
            log.warn("檢測到發送端斷開，回退至握手模式...");
        }

        if (currentState == AeronState.WAITING) {
            long now = System.currentTimeMillis();
            if (now - lastResumeSentTime > 200) { // 縮短至 200ms
                sendResumeSignalBlocking();
                lastResumeSentTime = now;
            }
        }
        // 關鍵：傳遞 assembler 而非直接傳遞 fragmentHandler
        return subscription.poll(assembler, 10);
    }

    private void sendResumeSignalBlocking() {
        AeronUtil.claimAndSend(controlPublication, bufferClaim, 12, idleStrategy, running, (buffer, offset) -> {
            buffer.putInt(offset, MsgType.RESUME);
            buffer.putLong(offset + 4, progress.getLastProcessedSeq());
        });
    }

    private final FragmentHandler fragmentHandler = (buffer, offset, length, header) -> {
        final int msgType = buffer.getInt(offset);
        final long gwSeq = buffer.getLong(offset + 4);
        final long lastSeq = progress.getLastProcessedSeq();
        
        // 1. 握手與自愈邏輯
        if (currentState == AeronState.WAITING) {
            if (gwSeq == 0 && lastSeq > 1000) {
                log.warn("檢測到發送端 WAL 重置 (gwSeq=0, lastProcessed={})，執行進度重置", lastSeq);
                progress.setLastProcessedSeq(-1L);
            } else if (gwSeq > lastSeq) {
                currentState = AeronState.SENDING;
                log.info("已與發送端對齊進度 (gwSeq: {})，握手成功", gwSeq);
            }
        }

        // 2. 冪等與連續性檢查
        if (gwSeq <= progress.getLastProcessedSeq()) return;
        
        if (currentState == AeronState.SENDING && gwSeq != lastSeq + 1 && lastSeq != -1) {
            log.error("指令鏈路跳號！期望: {}, 實際: {}。將強制對齊位點。", lastSeq + 1, gwSeq);
        }

        // 事務寫入：消除 Lambda
        try (DocumentContext dc = gwToMatchingWal.acquireAppender().writingDocument()) {
            dc.wire().write(ChronicleWireKey.msgType).int32(msgType);
            dc.wire().write(ChronicleWireKey.gwSeq).int64(gwSeq);
            if (msgType == MsgType.AUTH) {
                dc.wire().write(ChronicleWireKey.userId).int64(buffer.getLong(offset + 12));
            } else if (msgType == MsgType.ORDER_CREATE) {
                dc.wire().write(ChronicleWireKey.payload).bytes(ThreadContext.get().getPointerMapper().wrap(buffer, offset + 12, length - 12));
            } else if (msgType == MsgType.ORDER_CANCEL) {
                dc.wire().write(ChronicleWireKey.userId).int64(buffer.getLong(offset + 12));
                dc.wire().write(ChronicleWireKey.data).int64(buffer.getLong(offset + 20));
            } else if (msgType == MsgType.DEPOSIT) {
                dc.wire().write(ChronicleWireKey.userId).int64(buffer.getLong(offset + 12));
                dc.wire().write(ChronicleWireKey.assetId).int32(buffer.getInt(offset + 20));
                dc.wire().write(ChronicleWireKey.data).int64(buffer.getLong(offset + 24));
            }
        }
        
        progress.setLastProcessedSeq(gwSeq);
        metadata.put(MetaDataKey.MACHING_RECEVIER_POINT, progress);
    };

    @Override
    protected void onStop() { 
        if (subscription != null) subscription.close();
        if (controlPublication != null) controlPublication.close();
        ThreadContext.cleanup();
    }
}
