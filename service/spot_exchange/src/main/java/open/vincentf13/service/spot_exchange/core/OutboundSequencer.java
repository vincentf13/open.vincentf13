package open.vincentf13.service.spot_exchange.core;

import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/** 
  回報定序器
  負責將輸出事件寫入 Chronicle Queue
 */
@Component
public class OutboundSequencer {
    private ChronicleQueue queue;

    @PostConstruct
    public void init() {
        queue = SingleChronicleQueueBuilder
            .binary("data/spot_exchange/outbound-queue")
            .build();
    }

    public ChronicleQueue getQueue() {
        return queue;
    }

    @PreDestroy
    public void close() {
        if (queue != null) queue.close();
    }
}
