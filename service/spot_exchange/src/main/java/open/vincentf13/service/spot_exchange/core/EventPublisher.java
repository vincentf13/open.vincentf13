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
@Component
public class EventPublisher implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);

    private final OutboundSequencer outbound;
    private final ExchangeWebSocketHandler wsHandler;
    
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ExecutorService executor;

    public EventPublisher(OutboundSequencer outbound, ExchangeWebSocketHandler wsHandler) {
        this.outbound = outbound;
        this.wsHandler = wsHandler;
    }

    @PostConstruct
    public void start() {
        running.set(true);
        executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "event-publisher");
            t.setDaemon(true);
            return t;
        });
        executor.submit(this);
    }

    @Override
    public void run() {
        ExcerptTailer tailer = outbound.getQueue().createTailer();
        
        while (running.get()) {
            boolean handled = tailer.readDocument(wire -> {
                String topic = wire.read("topic").text();
                long userId = wire.read("userId").int64();
                
                // 根據 topic 封裝成 JSON 並發送
                if ("execution".equals(topic)) {
                    long orderId = wire.read("orderId").int64();
                    String status = wire.read("status").text();
                    wsHandler.sendMessage(String.valueOf(userId), 
                        String.format("{\"topic\":\"execution\",\"data\":{\"orderId\":%d,\"status\":\"%s\"}}", orderId, status));
                } else if ("auth.success".equals(topic)) {
                    wsHandler.sendMessage(String.valueOf(userId), "{\"topic\":\"auth\",\"status\":\"success\"}");
                }
                // ... 處理其他 topic
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
