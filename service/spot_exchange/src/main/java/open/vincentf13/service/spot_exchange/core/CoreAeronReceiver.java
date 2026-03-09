package open.vincentf13.service.spot_exchange.core;

import io.aeron.Aeron;
import io.aeron.Subscription;
import io.aeron.logbuffer.FragmentHandler;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.SigInt;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** 
  Core Aeron 接收器
  負責從 Aeron 訂閱訊息並寫入 Core WAL (core-queue)
 */
@Component
public class CoreAeronReceiver implements Runnable {
    private final Aeron aeron;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ChronicleQueue coreQueue;
    private Subscription subscription;
    private ExecutorService executor;

    public CoreAeronReceiver(Aeron aeron) {
        this.aeron = aeron;
    }

    @PostConstruct
    public void start() {
        coreQueue = SingleChronicleQueueBuilder.binary("data/spot_exchange/core-queue").build();
        subscription = aeron.addSubscription("aeron:udp?endpoint=localhost:40444", 10);
        
        running.set(true);
        executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "core-aeron-receiver"));
        executor.submit(this);
    }

    @Override
    public void run() {
        FragmentHandler fragmentHandler = (buffer, offset, length, header) -> {
            byte[] data = new byte[length];
            buffer.getBytes(offset, data);
            
            // 寫入 Core WAL
            coreQueue.acquireAppender().writeDocument(wire -> {
                wire.bytes().write(data);
                // 這裡應綁定 Aeron Sequence ID 以實現下游追蹤上游
                wire.write("aeronSeq").int64(header.sessionId()); 
            });
        };

        IdleStrategy idleStrategy = new BackoffIdleStrategy();
        while (running.get()) {
            int fragments = subscription.poll(fragmentHandler, 10);
            idleStrategy.idle(fragments);
        }
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        if (executor != null) executor.shutdown();
        if (coreQueue != null) coreQueue.close();
    }
}
