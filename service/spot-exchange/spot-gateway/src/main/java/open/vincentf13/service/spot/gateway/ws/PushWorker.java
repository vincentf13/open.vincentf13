package open.vincentf13.service.spot.gateway.ws;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
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
 WebSocket 推送背景執行緒 (PushWorker) - 終極性能版
 職責：讀取回報流並直接序列化至 ByteBuf 推送，達成全路徑零分配
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

    @Data
    private static class PushEvent {
        long orderId;
        String status;
        long cid;
        long userId;
    }

    private final PushEvent pushEventReusable = new PushEvent();
    private final JsonUtil.Envelope envelopeReusable = new JsonUtil.Envelope("execution", pushEventReusable);

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

                // 1. 填充載體
                pushEventReusable.setOrderId(executionDecoder.orderId());
                int statusOrdinal = executionDecoder.status().value();
                pushEventReusable.setStatus(statusOrdinal >= 0 && statusOrdinal < ORDER_STATUS_STRINGS.length ? 
                        ORDER_STATUS_STRINGS[statusOrdinal] : "UNKNOWN");
                pushEventReusable.setCid(executionDecoder.clientOrderId());
                pushEventReusable.setUserId(executionDecoder.userId());

                // 2. 優化點：從 Netty 池分配 ByteBuf，直接寫入 JSON
                ByteBuf outBuf = PooledByteBufAllocator.DEFAULT.buffer(512);
                JsonUtil.writeToByteBuf(outBuf, envelopeReusable);

                // 3. 推送 (sendMessage 負責 data.release())
                wsHandler.sendMessage(pushEventReusable.getUserId(), outBuf);
                
                // 注意：TextWebSocketFrame 會在發送後釋放 ByteBuf (由 Netty 負責)
                // 但為了保證安全，sendMessage 內部使用了 data.retain()

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
