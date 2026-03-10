package open.vincentf13.service.spot_exchange.core;

import io.aeron.Aeron;
import io.aeron.logbuffer.FragmentHandler;
import jakarta.annotation.PostConstruct;
import open.vincentf13.service.spot_exchange.infra.AeronSubscriber;
import open.vincentf13.service.spot_exchange.infra.BusySpinWorker;
import open.vincentf13.service.spot_exchange.infra.StateStore;
import org.agrona.concurrent.UnsafeBuffer;
import org.springframework.stereotype.Component;
import net.openhft.chronicle.bytes.PointerBytesStore;

import static open.vincentf13.service.spot_exchange.infra.ExchangeConstants.INBOUND_CHANNEL;
import static open.vincentf13.service.spot_exchange.infra.ExchangeConstants.INBOUND_STREAM_ID;

/** 
  Core Aeron 接收器
  負責從 Aeron 訂閱訊息並寫入 Core WAL (core-queue)
 */
@Component
public class CoreAeronReceiver extends BusySpinWorker {
    private final Aeron aeron;
    private final StateStore stateStore;
    private AeronSubscriber subscriber;
    private FragmentHandler fragmentHandler;
    
    // 預分配指針，用於 Zero-GC 橋接 Aeron 內存到 Chronicle Queue
    private final PointerBytesStore pointerBytesStore = new PointerBytesStore();

    public CoreAeronReceiver(Aeron aeron, StateStore stateStore) {
        this.aeron = aeron;
        this.stateStore = stateStore;
    }

    @PostConstruct
    public void init() {
        start("core-aeron-receiver");
    }

    @Override
    protected void onStart() {
        subscriber = new AeronSubscriber(aeron, INBOUND_CHANNEL, INBOUND_STREAM_ID);
        
        fragmentHandler = (buffer, offset, length, header) -> {
            // --- 修正：使用 PointerBytesStore 橋接地址，解決 bytes(...) 編譯錯誤 ---
            pointerBytesStore.set(buffer.addressOffset() + offset, length);
            
            stateStore.getCoreQueue().acquireAppender().writeDocument(wire -> {
                wire.write("msgType").int32(100); 
                wire.write("payload").bytes(pointerBytesStore); // 傳入 BytesStore
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
    }
}
