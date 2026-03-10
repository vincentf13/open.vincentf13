package open.vincentf13.service.spot_exchange.gateway;

import io.aeron.Aeron;
import jakarta.annotation.PostConstruct;
import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.queue.ExcerptTailer;
import org.agrona.concurrent.UnsafeBuffer;
import org.springframework.stereotype.Component;
import open.vincentf13.service.spot_exchange.infra.*;
import open.vincentf13.service.spot_exchange.model.SystemProgress;

import java.nio.ByteBuffer;

import static open.vincentf13.service.spot_exchange.infra.ExchangeConstants.*;

@Component
public class GatewayAeronSender extends BusySpinWorker {
    private final Aeron aeron;
    private final StateStore stateStore;
    private AeronPublisher publisher;
    private ExcerptTailer tailer;
    private final SystemProgress progress = new SystemProgress();
    
    private final UnsafeBuffer aeronBuffer = new UnsafeBuffer(0, 0);
    private final Bytes<ByteBuffer> reusableBytes = Bytes.elasticByteBuffer(1024);

    public GatewayAeronSender(Aeron aeron, StateStore stateStore) {
        this.aeron = aeron;
        this.stateStore = stateStore;
    }

    @PostConstruct public void init() { start("gw-aeron-sender"); }

    @Override
    protected void onStart() {
        publisher = new AeronPublisher(aeron, INBOUND_CHANNEL, INBOUND_STREAM_ID);
        tailer = stateStore.getGwQueue().createTailer();
        SystemProgress saved = stateStore.getSystemMetadataMap().get(PK_GW_INBOUND_SEQ);
        if (saved != null) {
            progress.setLastProcessedSeq(saved.getLastProcessedSeq());
            tailer.moveToIndex(progress.getLastProcessedSeq());
        }
    }

    @Override
    protected int doWork() {
        boolean handled = tailer.readDocument(wire -> {
            long seq = tailer.index();
            reusableBytes.clear();
            wire.read("payload").bytes(reusableBytes);
            aeronBuffer.wrap(reusableBytes.addressForRead(reusableBytes.readPosition()), (int)reusableBytes.readRemaining());

            while (publisher.tryPublish(aeronBuffer, 0, aeronBuffer.capacity()) < 0) {
                if (!running.get()) return;
                idleStrategy.idle();
            }
            progress.setLastProcessedSeq(seq);
        });

        if (handled) {
            // 每處理一條訊息後，視情況決定是否持久化位點（此處可進一步加入批次邏輯）
            // 為了簡單起見，我們在 doWork 返回 1 時，讓 BusySpinWorker 的 idleStrategy 處理
            // 我們在 onStop 中確保最後一次位點被寫入
            return 1;
        } else {
            // 空閒時持久化位點
            if (progress.getLastProcessedSeq() != -1) {
                stateStore.getSystemMetadataMap().put(PK_GW_INBOUND_SEQ, progress);
            }
            return 0;
        }
    }

    @Override
    protected void onStop() { 
        if (progress.getLastProcessedSeq() != -1) {
            stateStore.getSystemMetadataMap().put(PK_GW_INBOUND_SEQ, progress);
        }
        if (publisher != null) publisher.close();
        reusableBytes.releaseLast(); 
    }
}
