package open.vincentf13.service.spot_exchange.core;

import jakarta.annotation.PostConstruct;
import net.openhft.chronicle.queue.ExcerptTailer;
import org.agrona.concurrent.UnsafeBuffer;
import org.springframework.stereotype.Component;
import open.vincentf13.service.spot_exchange.gateway.ExchangeWebSocketHandler;
import open.vincentf13.service.spot_exchange.infra.BusySpinWorker;
import open.vincentf13.service.spot_exchange.infra.SbeCodec;
import open.vincentf13.service.spot_exchange.infra.StateStore;
import open.vincentf13.service.spot_exchange.sbe.ExecutionReportDecoder;

import java.nio.ByteBuffer;

@Component
public class EventPublisher extends BusySpinWorker {
    private final StateStore stateStore;
    private final ExchangeWebSocketHandler wsHandler;
    private ExcerptTailer tailer;

    private final ExecutionReportDecoder executionDecoder = new ExecutionReportDecoder();
    private final UnsafeBuffer payloadBuffer = new UnsafeBuffer(0, 0);

    public EventPublisher(StateStore stateStore, ExchangeWebSocketHandler wsHandler) {
        this.stateStore = stateStore;
        this.wsHandler = wsHandler;
    }

    @PostConstruct
    public void init() { start("event-publisher"); }

    @Override
    protected void onStart() {
        this.tailer = stateStore.getOutboundQueue().createTailer();
        // --- 深度優化：從持久化進度恢復，確保回報不遺失 ---
        Long lastSeq = stateStore.getSystemStateMap().get("lastPublishedSeq");
        if (lastSeq != null && lastSeq > 0) {
            tailer.moveToIndex(lastSeq);
        }
    }

    @Override
    protected int doWork() {
        return tailer.readDocument(wire -> {
            long currentIndex = tailer.index();
            int msgType = wire.read("msgType").int32();
            
            if (msgType == executionDecoder.sbeTemplateId()) {
                net.openhft.chronicle.bytes.Bytes<?> bytes = wire.read("payload").bytes();
                payloadBuffer.wrap(bytes.addressForRead(bytes.readPosition()), (int)bytes.readRemaining());
                SbeCodec.decode(payloadBuffer, 0, executionDecoder);
                
                String json = String.format(
                    "{\"topic\":\"execution\",\"data\":{\"orderId\":%d,\"status\":\"%s\",\"cid\":\"%s\"}}",
                    executionDecoder.orderId(), executionDecoder.status(), executionDecoder.clientOrderId()
                );
                wsHandler.sendMessage(String.valueOf(executionDecoder.userId()), json);
            }
            
            // 更新推送進度
            stateStore.getSystemStateMap().put("lastPublishedSeq", currentIndex);
        }) ? 1 : 0;
    }

    @Override
    protected void onStop() {}
}
