package open.vincentf13.service.spot_exchange.gateway;

import jakarta.annotation.PostConstruct;
import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.queue.ExcerptTailer;
import org.agrona.concurrent.UnsafeBuffer;
import org.springframework.stereotype.Component;
import open.vincentf13.service.spot_exchange.infra.*;
import open.vincentf13.service.spot_exchange.model.SystemProgress;
import open.vincentf13.service.spot_exchange.sbe.ExecutionReportDecoder;

import java.nio.ByteBuffer;

import static open.vincentf13.service.spot_exchange.infra.ExchangeConstants.*;

@Component
public class EventPublisher extends BusySpinWorker {
    private final StateStore stateStore;
    private final ExchangeWebSocketHandler wsHandler;
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
                
                String json = String.format(
                    "{\"topic\":\"execution\",\"data\":{\"orderId\":%d,\"status\":\"%s\",\"cid\":\"%s\"}}",
                    executionDecoder.orderId(), executionDecoder.status(), executionDecoder.clientOrderId()
                );
                wsHandler.sendMessage(String.valueOf(executionDecoder.userId()), json);
            }
            progress.setLastProcessedSeq(seq);
            stateStore.getSystemMetadataMap().getSystemMetadataMap().put(PK_PUB_PUSH_SEQ, progress);
        });
        return handled ? 1 : 0;
    }

    @Override
    protected void onStop() { 
        reusableBytes.releaseLast(); 
    }
}
