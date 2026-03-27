package open.vincentf13.service.spot.ws.aeron;

import io.aeron.Aeron;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.logbuffer.FragmentHandler;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.wire.WireIn;
import open.vincentf13.service.spot.infra.aeron.*;
import open.vincentf13.service.spot.infra.aeron.AeronConstants.AeronState;
import open.vincentf13.service.spot.infra.alloc.OffHeapUtil;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.infra.metrics.StaticMetricsHolder;
import open.vincentf13.service.spot.infra.thread.ThreadContext;
import open.vincentf13.service.spot.infra.thread.Worker;
import open.vincentf13.service.spot.model.command.AbstractSbeModel;
import org.springframework.stereotype.Component;

import static open.vincentf13.service.spot.infra.Constants.*;
import static org.agrona.UnsafeAccess.UNSAFE;

/** 網關 Aeron 發送器 (極簡扁平化版) */
@Slf4j
@Component
public class AeronSender extends Worker {
    private final ChronicleQueue wal;
    private Publication publication;
    private Subscription controlSub;
    private ExcerptTailer tailer;
    private AeronState currentState = AeronState.WAITING;
    private long localBackPressure = 0;

    public AeronSender(Aeron aeron) {
        super("gw-sender", MetricsKey.CPU_ID_AERON_SENDER, MetricsKey.CPU_ID_CURRENT_AERON_SENDER, MetricsKey.GATEWAY_AERON_SENDER_WORKER_DUTY_CYCLE);
        this.wal = Storage.self().gatewaySenderWal();
    }

    @PostConstruct @Override public void start() { super.start(); }
    @PreDestroy @Override public void stop() { super.stop(); }

    @Override
    protected void onStart() {
        this.publication = AeronClientHolder.aeron().addPublication(AeronChannel.MATCHING_FLOW, AeronChannel.DATA_STREAM_ID);
        this.controlSub = AeronClientHolder.aeron().addSubscription(AeronChannel.REPORT_FLOW, AeronChannel.CONTROL_STREAM_ID);
        this.tailer = wal.createTailer();
    }

    @Override
    protected int doWork() {
        if (currentState == AeronState.SENDING && !publication.isConnected()) currentState = AeronState.WAITING;

        int work = controlSub.poll(resumeHandler, AeronConstants.AERON_POLL_LIMIT);
        if (currentState == AeronState.WAITING) return work;

        for (int i = 0; i < 1000; i++) {
            long lastIdx = tailer.index();
            try (var dc = tailer.readingDocument()) {
                if (!dc.isPresent()) break;
                onWalMessage(dc.wire());
                if (dc.wire().bytes().readRemaining() > 0) {
                    tailer.moveToIndex(lastIdx); break;
                }
                StaticMetricsHolder.addCounter(MetricsKey.AERON_SEND_COUNT, 1);
                work++;
            }
        }
        return work;
    }

    private final FragmentHandler resumeHandler = (buffer, offset, length, header) -> {
        if (buffer.getInt(offset) == MsgType.RESUME && currentState == AeronState.WAITING) {
            long walIndex = buffer.getLong(offset + AeronConstants.MSG_SEQ_OFFSET);
            log.info("✅ 握手成功！位點: {}", walIndex);
            if (walIndex == WAL_INDEX_NONE || walIndex == MSG_SEQ_NONE || !tailer.moveToIndex(walIndex)) tailer.toStart();
            currentState = AeronState.SENDING;
        }
    };

    private void onWalMessage(WireIn wire) {
        final net.openhft.chronicle.bytes.Bytes<?> bytes = wire.bytes();
        final int len = (int) bytes.readRemaining();
        if (len <= 0) return;

        final long addr = bytes.addressForRead(bytes.readPosition());
        final long seq = tailer.index();

        while (running.get()) {
            int sendResult = AeronUtil.send(publication, len, (buf, off) -> {
                UNSAFE.copyMemory(addr, OffHeapUtil.getAddress(buf, off), len);
                buf.putLong(off + AbstractSbeModel.SEQ_OFFSET, seq);
            }, running);

            if (sendResult == 0) {
                bytes.readSkip(len);
                return;
            }
            if (handleSendFailure(sendResult)) return;
        }
    }

    private boolean handleSendFailure(int sendResult) {
        if (sendResult == -2) { localBackPressure++; Thread.onSpinWait(); return false; }
        if (sendResult == -1) { currentState = AeronState.WAITING; return true; }
        if (sendResult == -3) throw new IllegalStateException("Aeron publication is unavailable");
        return true;
    }

    @Override
    protected void onMetricsReport() {
        if (localBackPressure > 0) {
            StaticMetricsHolder.addCounter(MetricsKey.AERON_BACKPRESSURE, localBackPressure);
            localBackPressure = 0;
        }
    }

    @Override
    protected void onStop() {
        if (publication != null) publication.close();
        if (controlSub != null) controlSub.close();
        ThreadContext.cleanup();
    }
}
