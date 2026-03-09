package open.vincentf13.service.spot_exchange.core;

import net.openhft.chronicle.queue.ExcerptTailer;
import open.vincentf13.service.spot_exchange.gateway.ExchangeWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** 
  事件發布器
  輪詢 Outbound Queue 並透過 WebSocket 回傳給用戶
 */
import open.vincentf13.service.spot_exchange.sbe.ExecutionReportDecoder;
import open.vincentf13.service.spot_exchange.sbe.MessageHeaderDecoder;

@Component
public class EventPublisher implements Runnable {
    // ... 前面已有的欄位 ...
    private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    private final ExecutionReportDecoder executionDecoder = new ExecutionReportDecoder();
    private final org.agrona.concurrent.UnsafeBuffer payloadBuffer = new org.agrona.concurrent.UnsafeBuffer(java.nio.ByteBuffer.allocateDirect(512));

    @Override
    public void run() {
        ExcerptTailer tailer = outbound.getQueue().createTailer();
        
        while (running.get()) {
            boolean handled = tailer.readDocument(wire -> {
                int msgType = wire.read("msgType").int32();
                byte[] bytes = wire.read("payload").bytes();
                payloadBuffer.putBytes(0, bytes);

                if (msgType == executionDecoder.sbeTemplateId()) {
                    headerDecoder.wrap(payloadBuffer, 0);
                    executionDecoder.wrap(payloadBuffer, 
                                         MessageHeaderDecoder.ENCODED_LENGTH, 
                                         headerDecoder.blockLength(), 
                                         headerDecoder.version());
                    
                    long userId = executionDecoder.userId();
                    String json = String.format(
                        "{\"topic\":\"execution\",\"data\":{\"orderId\":%d,\"status\":\"%s\",\"cid\":\"%s\"}}",
                        executionDecoder.orderId(), executionDecoder.status(), executionDecoder.clientOrderId()
                    );
                    wsHandler.sendMessage(String.valueOf(userId), json);
                }
            });

            if (!handled) {
                Thread.onSpinWait();
            }
        }
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        if (executor != null) executor.shutdown();
    }
}
