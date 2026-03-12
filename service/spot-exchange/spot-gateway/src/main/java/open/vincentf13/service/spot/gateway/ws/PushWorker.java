package open.vincentf13.service.spot.gateway.ws;

import jakarta.annotation.PostConstruct;
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
import java.util.Map;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 WebSocket 推送背景執行緒 (PushWorker)
 職責：讀取核心回報流 (Matching -> Gateway) 並推送至客戶端
 */
@Slf4j
@Component
public class PushWorker extends Worker {
    // 依賴的具體存儲結構 (反映 Matching -> Gateway 流向)
    private final ChronicleQueue matchingToGwWal = Storage.self().matchingToGwWal();
    private final ChronicleMap<Byte, Progress> metadata = Storage.self().metadata();

    private final WsHandler wsHandler;
    private ExcerptTailer tailer;
    private final Progress progress = new Progress();

    private final ExecutionReportDecoder executionDecoder = new ExecutionReportDecoder();
    private final UnsafeBuffer payloadBuffer = new UnsafeBuffer(0, 0);
    private final Bytes<ByteBuffer> reusableBytes = Bytes.elasticByteBuffer(1024);

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
            int msgType = wire.read(ChronicleWireKey.msgType).int32();

            if (msgType == executionDecoder.sbeTemplateId()) {
                reusableBytes.clear(); wire.read(ChronicleWireKey.payload).bytes(reusableBytes);
                payloadBuffer.wrap(reusableBytes.addressForRead(reusableBytes.readPosition()), (int)reusableBytes.readRemaining());
                SbeCodec.decode(payloadBuffer, 0, executionDecoder);

                Map<String, Object> data = Map.of(
                    "orderId", executionDecoder.orderId(),
                    "status", executionDecoder.status().toString(),
                    "cid", executionDecoder.clientOrderId(),
                    "userId", executionDecoder.userId()
                );
                String json = JsonUtil.toJson("execution", data);
                wsHandler.sendMessage(String.valueOf(executionDecoder.userId()), json);
            }
            progress.setLastProcessedSeq(seq);
            metadata.put(MetaDataKey.WS_PUSH_TO_CLIENT_POINT, progress);
        });
        return handled ? 1 : 0;
    }

    @Override
    protected void onStop() { reusableBytes.releaseLast(); }
}
