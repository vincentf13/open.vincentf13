package open.vincentf13.service.spot.gateway.ws;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.bytes.Bytes;
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

import static open.vincentf13.service.spot.infra.Constants.PK_PUB_PUSH_SEQ;

@Slf4j
@Component
public class PushWorker extends Worker {
    private final WsHandler wsHandler;
    private ExcerptTailer tailer;
    private final Progress progress = new Progress();

    private final ExecutionReportDecoder executionDecoder = new ExecutionReportDecoder();
    private final UnsafeBuffer payloadBuffer = new UnsafeBuffer(0, 0);
    private final Bytes<ByteBuffer> reusableBytes = Bytes.elasticByteBuffer(1024);

    public PushWorker(WsHandler wsHandler) {
        this.wsHandler = wsHandler;
    }

    @PostConstruct public void init() { start("push-worker"); }
    
    @Override
    protected void onStart() {
        this.tailer = Storage.self().resultQueue().createTailer();
        Progress saved = Storage.self().metadata().get(PK_PUB_PUSH_SEQ);
        if (saved != null) {
            progress.setLastProcessedSeq(saved.getLastProcessedSeq());
            tailer.moveToIndex(progress.getLastProcessedSeq());
        } else progress.setLastProcessedSeq(-1L);
    }

    @Override
    protected int doWork() {
        boolean handled = tailer.readDocument(wire -> {
            long seq = tailer.index();
            int msgType = wire.read("msgType").int32();

            if (msgType == executionDecoder.sbeTemplateId()) {
                reusableBytes.clear(); wire.read("payload").bytes(reusableBytes);
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
            Storage.self().metadata().put(PK_PUB_PUSH_SEQ, progress);
        });
        return handled ? 1 : 0;
    }

    @Override
    protected void onStop() { reusableBytes.releaseLast(); }
}
