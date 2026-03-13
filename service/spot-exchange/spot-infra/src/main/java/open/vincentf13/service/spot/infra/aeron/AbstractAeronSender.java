package open.vincentf13.service.spot.infra.aeron;

import io.aeron.Aeron;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.logbuffer.BufferClaim;
import io.aeron.logbuffer.FragmentHandler;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.wire.ReadMarshallable;
import open.vincentf13.service.spot.infra.Worker;
import open.vincentf13.service.spot.infra.alloc.ThreadContext;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.IdleStrategy;

/** 
 Aeron 發送器抽象基類 (Abstract Aeron Sender)
 職責：管理 Aeron 連線生命週期，並提供 WAL 指令流的讀取與發送機制
 */
@Slf4j
public abstract class AbstractAeronSender extends Worker implements ReadMarshallable {
    protected final Aeron aeron;
    protected final ChronicleQueue wal;
    protected final String dataUrl;
    protected final int dataStreamId;
    protected final String controlUrl;
    protected final int controlStreamId;

    protected Publication publication;
    protected Subscription controlSubscription;
    protected final BufferClaim bufferClaim = new BufferClaim();
    protected final IdleStrategy idleStrategy = new BackoffIdleStrategy();
    
    protected AeronClient aeronClient;
    protected ExcerptTailer tailer;
    protected long backPressureCount = 0;

    public AbstractAeronSender(Aeron aeron, ChronicleQueue wal, String dataUrl, int dataStreamId, String controlUrl, int controlStreamId) {
        this.aeron = aeron;
        this.wal = wal;
        this.dataUrl = dataUrl;
        this.dataStreamId = dataStreamId;
        this.controlUrl = controlUrl;
        this.controlStreamId = controlStreamId;
    }

    @Override
    protected void onStart() {
        this.publication = aeron.addPublication(dataUrl, dataStreamId);
        this.controlSubscription = aeron.addSubscription(controlUrl, controlStreamId);
        this.tailer = wal.createTailer();
        this.aeronClient = new AeronClient(publication, bufferClaim, idleStrategy, running);
        
        log.info("AeronSender 啟動成功: DataUrl={}, ControlUrl={}", dataUrl, controlUrl);
    }

    @Override
    protected int doWork() {
        // 1. 處理控制流 (如握手、心跳)
        controlSubscription.poll(controlFragmentHandler, 10);
        
        // 2. 處理 WAL 指令流 (讀取一筆並發送)
        return tailer.readDocument(this) ? 1 : 0;
    }

    /** 控制流處理邏輯 (可由子類擴展) */
    private final FragmentHandler controlFragmentHandler = (buffer, offset, length, header) -> {
        // 暫時為空，預留給握手機制
    };

    @Override
    protected void onStop() {
        if (publication != null) publication.close();
        if (controlSubscription != null) controlSubscription.close();
        ThreadContext.cleanup();
    }
}
