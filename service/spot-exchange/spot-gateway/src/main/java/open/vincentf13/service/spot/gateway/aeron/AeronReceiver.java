package open.vincentf13.service.spot.gateway.aeron;

import io.aeron.Aeron;
import io.aeron.FragmentAssembler;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.logbuffer.BufferClaim;
import io.aeron.logbuffer.FragmentHandler;
import jakarta.annotation.PostConstruct;
import open.vincentf13.service.spot.infra.Worker;
import open.vincentf13.service.spot.infra.aeron.AeronUtil;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.model.Progress;
import org.springframework.stereotype.Component;
import net.openhft.chronicle.bytes.PointerBytesStore;
import net.openhft.chronicle.map.ChronicleMap;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.wire.DocumentContext;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 Gateway Aeron 接收器
 職責：監聽核心引擎回報並落地 WAL，達成零物件分配
 */
@Component
public class AeronReceiver extends Worker {
    private final ChronicleQueue matchingToGwWal = Storage.self().matchingToGwWal();
    private final ChronicleMap<Byte, Progress> metadata = Storage.self().metadata();

    private final Aeron aeron;
    private Subscription subscription;
    private Publication controlPublication;
    private final BufferClaim bufferClaim = new BufferClaim();
    private final Progress progress = new Progress();
    private final PointerBytesStore pointerBytesStore = new PointerBytesStore();
    
    // 支援網路分片訊息重組
    private FragmentAssembler assembler;

    private AeronState currentState = AeronState.WAITING;
    private long lastResumeSentTime = 0;

    public AeronReceiver(Aeron aeron) {
        this.aeron = aeron;
    }

    @PostConstruct public void init() { start("gw-result-receiver"); }

    @Override
    protected void onStart() {
        subscription = aeron.addSubscription(AeronChannel.GATEWAY_URL, AeronChannel.DATA_STREAM_ID);
        controlPublication = aeron.addPublication(AeronChannel.MATCHING_URL, AeronChannel.CONTROL_STREAM_ID);
        this.assembler = new FragmentAssembler(fragmentHandler);
        
        Progress saved = metadata.get(MetaDataKey.GW_RECEVIER_POINT);
        if (saved != null) progress.setLastProcessedSeq(saved.getLastProcessedSeq());
        else progress.setLastProcessedSeq(-1L);
        
        currentState = AeronState.WAITING;
        log.info("AeronReceiver (Gateway) 啟動：當前進度: {}，進入握手狀態...", progress.getLastProcessedSeq());
    }

    @Override
    protected int doWork() {
        if (currentState == AeronState.SENDING && !subscription.isConnected()) {
            currentState = AeronState.WAITING;
            log.warn("檢測到核心引擎斷開，重新進入握手探測模式...");
        }

        if (currentState == AeronState.WAITING) {
            long now = System.currentTimeMillis();
            if (now - lastResumeSentTime > 200) { // 縮短至 200ms
                sendResumeSignalBlocking();
                lastResumeSentTime = now;
            }
        }
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
        final long matchingSeq = buffer.getLong(offset + 4);
        final long lastSeq = progress.getLastProcessedSeq();
        
        // 1. 握手與自愈邏輯
        if (currentState == AeronState.WAITING) {
            if (matchingSeq == 0 && lastSeq > 1000) {
                log.warn("檢測到核心引擎重置 (matchingSeq=0)，執行進度重置");
                progress.setLastProcessedSeq(-1L);
            } else if (matchingSeq > lastSeq) {
                currentState = AeronState.SENDING;
                log.info("已與核心引擎對齊進度 (mSeq: {})，回報鏈路握手成功", matchingSeq);
            }
        }

        if (matchingSeq <= progress.getLastProcessedSeq()) return;

        if (currentState == AeronState.SENDING && matchingSeq != lastSeq + 1 && lastSeq != -1) {
            log.error("回報鏈路跳號！期望: {}, 實際: {}。將強制對齊位點。", lastSeq + 1, matchingSeq);
        }

        final long messageAddress = buffer.addressOffset() + offset;
        
        // 事務落地：對齊核心引擎的寫入格式，將純 SBE 資料放入 payload 欄位
        try (DocumentContext dc = matchingToGwWal.acquireAppender().writingDocument()) {
            dc.wire().write(ChronicleWireKey.msgType).int32(msgType);
            dc.wire().write(ChronicleWireKey.matchingSeq).int64(matchingSeq);
            
            // 跳過前 12 位元組 (Aeron Header)，提取純 SBE Payload
            pointerBytesStore.set(messageAddress + 12, length - 12);
            dc.wire().write(ChronicleWireKey.payload).bytes(pointerBytesStore);
        }

        progress.setLastProcessedSeq(matchingSeq);
        metadata.put(MetaDataKey.GW_RECEVIER_POINT, progress);
    };

    @Override
    protected void onStop() { 
        if (subscription != null) subscription.close();
        if (controlPublication != null) controlPublication.close();
    }
}
