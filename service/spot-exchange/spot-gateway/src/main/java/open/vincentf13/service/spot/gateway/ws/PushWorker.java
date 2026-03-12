package open.vincentf13.service.spot.gateway.ws;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.map.ChronicleMap;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptTailer;
import org.agrona.concurrent.UnsafeBuffer;
import org.springframework.stereotype.Component;
import open.vincentf13.service.spot.infra.sbe.SbeCodec;
import open.vincentf13.service.spot.infra.Worker;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.infra.util.JsonUtil;
import open.vincentf13.service.spot.model.Progress;
import open.vincentf13.service.spot.sbe.ExecutionReportDecoder;

import java.nio.ByteBuffer;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 WebSocket 推送背景執行緒 (PushWorker) - 終極 Zero-String 版
 職責：讀取回報流並推送，消除所有臨時 String 與 Map 分配
 */
@Slf4j
@Component
public class PushWorker extends Worker {
    private final ChronicleQueue matchingToGwWal = Storage.self().matchingToGwWal();
    private final ChronicleMap<Byte, Progress> metadata = Storage.self().metadata();

    private final WsHandler wsHandler;
    private ExcerptTailer tailer;
    private final Progress progress = new Progress();

    private final ExecutionReportDecoder executionDecoder = new ExecutionReportDecoder();
    private final UnsafeBuffer payloadBuffer = new UnsafeBuffer(0, 0);
    private final Bytes<ByteBuffer> reusableBytes = Bytes.elasticByteBuffer(4096);

    /** 預分配推送載體 */
    @Data
    private static class PushEvent {
        long orderId;
        String status; // 這裡將存儲快取中的靜態 String 引用
        long cid;
        long userId;
    }

    private final PushEvent pushEventReusable = new PushEvent();

    public PushWorker(WsHandler wsHandler) {
        this.wsHandler = wsHandler;
    }

    @PostConstruct public void init() { start("gw-push-worker"); }
    
    @Override
    protected void onStart() {
        this.tailer = matchingToGwWal.createTailer();
        Progress saved = metadata.get(MetaDataKey.WS_PUSH_TO_CLIENT_POINT);
        if (saved != null) {
            progress.setLastProcessedSeq(saved.getLastProcessedSeq());
            tailer.moveToIndex(progress.getLastProcessedSeq());
        } else progress.setLastProcessedSeq(-1L);
    }

    @Override
    protected int doWork() {
        boolean handled = tailer.readDocument(wire -> {
            long seq = tailer.index();
            reusableBytes.clear(); 
            wire.read(ChronicleWireKey.payload).bytes(reusableBytes);
            
            long address = reusableBytes.addressForRead(reusableBytes.readPosition());
            int totalLen = (int) reusableBytes.readRemaining();
            int offset = 0;

            while (offset < totalLen) {
                payloadBuffer.wrap(address + offset, totalLen - offset);
                SbeCodec.decode(payloadBuffer, 0, executionDecoder);
                int currentMsgLen = SbeCodec.BLOCK_AND_VERSION_HEADER_SIZE + executionDecoder.encodedLength();

                // 1. 零分配填充載體
                pushEventReusable.setOrderId(executionDecoder.orderId());
                
                // 2. 性能優化：使用靜態字串快取，避免 enum.toString() 導致的分配
                int statusOrdinal = executionDecoder.status().value();
                if (statusOrdinal >= 0 && statusOrdinal < ORDER_STATUS_STRINGS.length) {
                    pushEventReusable.setStatus(ORDER_STATUS_STRINGS[statusOrdinal]);
                } else {
                    pushEventReusable.setStatus("UNKNOWN");
                }
                
                pushEventReusable.setCid(executionDecoder.clientOrderId());
                pushEventReusable.setUserId(executionDecoder.userId());

                // 3. 序列化並推送 (直接傳遞 long 型 UID)
                wsHandler.sendMessage(pushEventReusable.getUserId(), 
                        JsonUtil.toJson("execution", pushEventReusable));

                offset += currentMsgLen;
            }

            progress.setLastProcessedSeq(seq);
            metadata.put(MetaDataKey.WS_PUSH_TO_CLIENT_POINT, progress);
        });
        return handled ? 1 : 0;
    }

    @Override
    protected void onStop() { reusableBytes.releaseLast(); }
}
