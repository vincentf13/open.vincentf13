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
import open.vincentf13.service.spot_exchange.infra.AeronSubscriber;
import open.vincentf13.service.spot_exchange.infra.BusySpinWorker;
import io.aeron.logbuffer.FragmentHandler;

@Component
public class CoreAeronReceiver extends BusySpinWorker {
    private final Aeron aeron;
    private ChronicleQueue coreQueue;
    private AeronSubscriber subscriber;
    private FragmentHandler fragmentHandler;

    public CoreAeronReceiver(Aeron aeron) {
        this.aeron = aeron;
    }

    @PostConstruct
    public void init() {
        start("core-aeron-receiver");
    }

    @Override
    protected void onStart() {
        coreQueue = SingleChronicleQueueBuilder.binary("data/spot_exchange/core-queue").build();
        subscriber = new AeronSubscriber(aeron, "aeron:udp?endpoint=localhost:40444", 10);
        
        fragmentHandler = (buffer, offset, length, header) -> {
            byte[] data = new byte[length];
            buffer.getBytes(offset, data);
            coreQueue.acquireAppender().writeDocument(wire -> {
                wire.bytes().write(data);
                wire.write("aeronSeq").int64(header.position()); 
            });
        };
    }

    @Override
    protected int doWork() {
        return subscriber.poll(fragmentHandler, 10);
    }

    @Override
    protected void onStop() {
        if (subscriber != null) subscriber.close();
        if (coreQueue != null) coreQueue.close();
    }
}
