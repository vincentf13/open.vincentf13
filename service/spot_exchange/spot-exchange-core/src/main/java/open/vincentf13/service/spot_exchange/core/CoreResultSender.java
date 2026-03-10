package open.vincentf13.service.spot_exchange.core;

import io.aeron.Aeron;
import jakarta.annotation.PostConstruct;
import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptTailer;
import org.agrona.concurrent.UnsafeBuffer;
import org.springframework.stereotype.Component;
import open.vincentf13.service.spot_exchange.infra.AeronPublisher;
import open.vincentf13.service.spot_exchange.infra.BusySpinWorker;
import open.vincentf13.service.spot_exchange.infra.StateStore;

import java.nio.ByteBuffer;

@Component
public class CoreResultSender extends BusySpinWorker {
    private final Aeron aeron;
    private final StateStore stateStore;
    private AeronPublisher publisher;
    private ExcerptTailer tailer;
    
    private final UnsafeBuffer aeronBuffer = new UnsafeBuffer(0, 0);
    private final Bytes<ByteBuffer> reusableBytes = Bytes.elasticByteBuffer(1024);

    public CoreResultSender(Aeron aeron, StateStore stateStore) {
        this.aeron = aeron;
        this.stateStore = stateStore;
    }

    @PostConstruct
    public void init() { start("core-result-sender"); }

    @Override
    protected void onStart() {
        publisher = new AeronPublisher(aeron, "aeron:udp?endpoint=localhost:40445", 30);
        tailer = stateStore.getOutboundQueue().createTailer();
        // --- 深度優化：從持久化進度恢復 ---
        Long lastSent = stateStore.getSystemStateMap().get("lastCoreResultSeq");
        if (lastSent != null && lastSent > 0) tailer.moveToIndex(lastSent);
    }

    @Override
    protected int doWork() {
        return tailer.readDocument(wire -> {
            long currentIndex = tailer.index();
            reusableBytes.clear();
            wire.read("payload").bytes(reusableBytes);
            
            aeronBuffer.wrap(reusableBytes.addressForRead(reusableBytes.readPosition()), (int)reusableBytes.readRemaining());
            
            while (publisher.tryPublish(aeronBuffer, 0, aeronBuffer.capacity()) < 0) {
                if (!running.get()) return;
                idleStrategy.idle();
            }
            // 更新進度
            stateStore.getSystemStateMap().put("lastCoreResultSeq", currentIndex);
        }) ? 1 : 0;
    }

    @Override
    protected void onStop() {
        if (publisher != null) publisher.close();
        if (reusableBytes != null) reusableBytes.releaseLast();
    }
}
