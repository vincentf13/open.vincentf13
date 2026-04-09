package open.vincentf13.service.spot.ws.aeron;

import com.lmax.disruptor.EventPoller;
import com.lmax.disruptor.RingBuffer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptAppender;
import net.openhft.chronicle.wire.Wire;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.infra.chronicle.WalField;
import open.vincentf13.service.spot.infra.Constants.*;
import open.vincentf13.service.spot.infra.metrics.StaticMetricsHolder;
import open.vincentf13.service.spot.infra.thread.Worker;
import open.vincentf13.service.spot.infra.util.PreTouchUtil;
import open.vincentf13.service.spot.ws.wal.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.File;

@Slf4j
@Component
@ConditionalOnProperty(name = "spot.wal.bypass", havingValue = "false", matchIfMissing = true)
public class WalWriter extends Worker {
    private final RingBuffer<WalEvent> ringBuffer;
    private final EventPoller<WalEvent> poller;
    private final ChronicleQueue wal;
    private ExcerptAppender appender;
    private Thread preTouchDaemon;
    private long localWriteCount;
    private int pollCount;

    public WalWriter(RingBuffer<WalEvent> ringBuffer) {
        super("wal-writer", MetricsKey.CPU_ID_WAL_SENDER, MetricsKey.CPU_ID_CURRENT_WAL_SENDER, MetricsKey.GATEWAY_WAL_SENDER_DUTY_CYCLE);
        this.ringBuffer = ringBuffer;
        this.poller = ringBuffer.newPoller();
        this.wal = Storage.self().gatewaySenderWal();
    }

    public com.lmax.disruptor.Sequence getSequence() {
        return poller.getSequence();
    }

    @PostConstruct @Override public void start() { super.start(); }
    @PreDestroy @Override public void stop() { super.stop(); }

    @Override
    protected void onStart() {
        PreTouchUtil.touchDirectory(new File(ChronicleMapEnum.WAL_BASE_DIR));
        this.appender = wal.acquireAppender();
        startPreTouchDaemon();
        log.info("[{}] 初始化完成", Thread.currentThread().getName());
    }

    private void startPreTouchDaemon() {
        preTouchDaemon = Thread.ofPlatform().daemon().name("wal-pretouch").start(() -> {
            ExcerptAppender pretouchAppender = wal.acquireAppender();
            while (running.get()) {
                try { Thread.sleep(50); } catch (InterruptedException e) { break; }
                try { pretouchAppender.pretouch(); } catch (Exception ignored) {}
            }
        });
    }

    private final EventPoller.Handler<WalEvent> writeHandler = (event, sequence, endOfBatch) -> {
        event.walIndex = writeToWal(event);
        localWriteCount++;
        pollCount++;
        return true;
    };

    @Override
    protected int doWork() {
        pollCount = 0;
        try {
            poller.poll(writeHandler);
        } catch (Exception e) {
            log.error("[WAL-WRITER] failed", e);
        }
        return pollCount;
    }

    private long writeToWal(WalEvent e) {
        try (var dc = appender.writingDocument()) {
            Wire wire = dc.wire();
            if (wire == null) return -1;

            wire.write(WalField.msgType).int32(e.msgType);
            wire.write(WalField.gwTime).int64(e.arrivalTimeNs);
            wire.write(WalField.userId).int64(e.userId);
            wire.write(WalField.timestamp).int64(e.timestamp);

            switch (e.msgType) {
                case MsgType.ORDER_CREATE -> {
                    WalOrderCreate oc = e.orderCreate;
                    wire.write(WalField.symbolId).int32(oc.symbolId);
                    wire.write(WalField.price).int64(oc.price);
                    wire.write(WalField.qty).int64(oc.qty);
                    wire.write(WalField.side).int8(oc.side);
                    wire.write(WalField.clientOrderId).int64(oc.clientOrderId);
                }
                case MsgType.ORDER_CANCEL -> wire.write(WalField.orderId).int64(e.orderCancel.orderId);
                case MsgType.DEPOSIT -> {
                    WalDeposit d = e.deposit;
                    wire.write(WalField.assetId).int32(d.assetId);
                    wire.write(WalField.amount).int64(d.amount);
                }
            }
            return dc.index();
        }
    }

    @Override
    protected void onMetricsReport() {
        if (localWriteCount > 0) {
            StaticMetricsHolder.addCounter(MetricsKey.GATEWAY_WAL_WRITE_COUNT, localWriteCount);
            localWriteCount = 0;
        }
    }

    @Override
    protected void onStop() {
        if (preTouchDaemon != null) preTouchDaemon.interrupt();
        log.info("[{}] 已停止", Thread.currentThread().getName());
    }
}
