package open.vincentf13.service.spot_exchange.core;

import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/** 
  指令定序器
  負責將輸入指令寫入 Chronicle Queue (WAL)
 */
@Component
public class InboundSequencer {
    private ChronicleQueue queue;

    @PostConstruct
    public void init() {
        queue = SingleChronicleQueueBuilder
            .binary("data/spot_exchange/inbound-queue")
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
