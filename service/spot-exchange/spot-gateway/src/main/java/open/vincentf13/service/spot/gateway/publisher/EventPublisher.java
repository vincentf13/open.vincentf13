package open.vincentf13.service.spot.gateway.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.queue.ExcerptTailer;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import open.vincentf13.service.spot.gateway.handler.ExchangeWebSocketHandler;
import open.vincentf13.service.spot.infra.codec.SbeCodec;
import open.vincentf13.service.spot.infra.worker.BusySpinWorker;
import open.vincentf13.service.spot.infra.store.StateStore;
import open.vincentf13.service.spot.model.SystemProgress;
import open.vincentf13.service.spot.sbe.ExecutionReportDecoder;

import java.nio.ByteBuffer;
import java.util.Map;

import static open.vincentf13.service.spot.infra.constant.ExchangeConstants.PK_PUB_PUSH_SEQ;

@Component
public class EventPublisher extends BusySpinWorker {
    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);
    private final StateStore stateStore;
    private final ExchangeWebSocketHandler wsHandler;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private ExcerptTailer tailer;
    private final SystemProgress progress = new SystemProgress();

    private final ExecutionReportDecoder executionDecoder = new ExecutionReportDecoder();
    private final UnsafeBuffer payloadBuffer = new UnsafeBuffer(0, 0);
    private final Bytes<ByteBuffer> reusableBytes = Bytes.elasticByteBuffer(1024);

    public EventPublisher(StateStore stateStore, ExchangeWebSocketHandler wsHandler) {
        this.stateStore = stateStore;
        this.wsHandler = wsHandler;
    }

    @PostConstruct public void init() { start("event-publisher"); }
    
    @Override
    protected void onStart() {
        this.tailer = stateStore.getOutboundQueue().createTailer();
        SystemProgress saved = stateStore.getSystemMetadataMap().get(PK_PUB_PUSH_SEQ);
        if (saved != null) {
            progress.setLastProcessedSeq(saved.getLastProcessedSeq());
            tailer.moveToIndex(progress.getLastProcessedSeq());
        } else {
            progress.setLastProcessedSeq(-1L);
        }
    }

    @Override
    protected int doWork() {
        boolean handled = tailer.readDocument(wire -> {
            long seq = tailer.index();
            int msgType = wire.read("msgType").int32();

            if (msgType == executionDecoder.sbeTemplateId()) {
                reusableBytes.clear();
                wire.read("payload").bytes(reusableBytes);
                payloadBuffer.wrap(reusableBytes.addressForRead(reusableBytes.readPosition()), (int)reusableBytes.readRemaining());
                SbeCodec.decode(payloadBuffer, 0, executionDecoder);

                Map<String, Object> data = Map.of(
                    "orderId", executionDecoder.orderId(),
                    "status", executionDecoder.status().toString(),
                    "cid", executionDecoder.clientOrderId(),
                    "userId", executionDecoder.userId()
                );
                try {
                    String json = objectMapper.writeValueAsString(Map.of("topic", "execution", "data", data));
                    wsHandler.sendMessage(String.valueOf(executionDecoder.userId()), json);
                } catch (Exception e) {
                    log.error("Serialize execution report error: {}", e.getMessage());
                }
            }
            progress.setLastProcessedSeq(seq);
            stateStore.getSystemMetadataMap().put(PK_PUB_PUSH_SEQ, progress);
        });
        return handled ? 1 : 0;
    }

    @Override
    protected void onStop() { 
        reusableBytes.releaseLast(); 
    }
}
