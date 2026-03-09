package open.vincentf13.service.spot_exchange.core;

import jakarta.annotation.PostConstruct;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;
import org.agrona.concurrent.UnsafeBuffer;
import org.springframework.stereotype.Component;
import open.vincentf13.service.spot_exchange.gateway.ExchangeWebSocketHandler;
import open.vincentf13.service.spot_exchange.infra.BusySpinWorker;
import open.vincentf13.service.spot_exchange.sbe.ExecutionReportDecoder;
import open.vincentf13.service.spot_exchange.sbe.MessageHeaderDecoder;

import java.nio.ByteBuffer;

/** 
  事件發布器
  輪詢 Outbound Queue 並透過 WebSocket 回傳給用戶
 */
@Component
public class EventPublisher extends BusySpinWorker {
    private final OutboundSequencer outbound;
    private final ExchangeWebSocketHandler wsHandler;
    
    private ChronicleQueue outboundQueue;
    private ExcerptTailer tailer;

    private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    private final ExecutionReportDecoder executionDecoder = new ExecutionReportDecoder();
    private final UnsafeBuffer payloadBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(512));

    public EventPublisher(OutboundSequencer outbound, ExchangeWebSocketHandler wsHandler) {
        this.outbound = outbound;
        this.wsHandler = wsHandler;
    }

    @PostConstruct
    public void init() {
        start("event-publisher");
    }

    @Override
    protected void onStart() {
        this.outboundQueue = SingleChronicleQueueBuilder.binary("data/spot_exchange/outbound-queue").build();
        this.tailer = outboundQueue.createTailer();
    }

    @Override
    protected int doWork() {
        boolean handled = tailer.readDocument(wire -> {
            int msgType = wire.read("msgType").int32();
            if (msgType == executionDecoder.sbeTemplateId()) {
                byte[] bytes = wire.read("payload").bytes();
                payloadBuffer.putBytes(0, bytes);
                headerDecoder.wrap(payloadBuffer, 0);
                executionDecoder.wrap(payloadBuffer, MessageHeaderDecoder.ENCODED_LENGTH, headerDecoder.blockLength(), headerDecoder.version());
                
                long userId = executionDecoder.userId();
                String json = String.format(
                    "{\"topic\":\"execution\",\"data\":{\"orderId\":%d,\"status\":\"%s\",\"cid\":\"%s\"}}",
                    executionDecoder.orderId(), executionDecoder.status(), executionDecoder.clientOrderId()
                );
                wsHandler.sendMessage(String.valueOf(userId), json);
            }
        });
        return handled ? 1 : 0;
    }

    @Override
    protected void onStop() {
        if (outboundQueue != null) outboundQueue.close();
    }
}
