package open.vincentf13.service.spot.ws.wal;

import com.lmax.disruptor.EventPoller;
import com.lmax.disruptor.RingBuffer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptAppender;
import net.openhft.chronicle.wire.Wire;
import open.vincentf13.service.spot.infra.alloc.PreTouchUtil;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.infra.chronicle.WalField;
import open.vincentf13.service.spot.infra.metrics.StaticMetricsHolder;
import open.vincentf13.service.spot.infra.thread.Worker;
import org.springframework.stereotype.Component;

import static open.vincentf13.service.spot.infra.Constants.*;

/**
 * 單一 WAL 寫入者 (Single WAL Writer)
 *
 * 遵循 Single Writer Principle：獨佔 ExcerptAppender，消除鎖競爭。
 * 透過 Disruptor EventPoller 非阻塞地排空 RingBuffer，
 * 以 BinaryWire 帶標籤格式寫入 Chronicle Queue，確保 WAL 版本相容。
 */
@Slf4j
@Component
public class WalWriter extends Worker {
    private final RingBuffer<WalEvent> ringBuffer;
    private final EventPoller<WalEvent> poller;
    private final ChronicleQueue wal;
    private ExcerptAppender appender;
    private long localWriteCount;

    public WalWriter(RingBuffer<WalEvent> ringBuffer) {
        super("wal-writer",
              MetricsKey.CPU_ID_WAL_WRITER,
              MetricsKey.CPU_ID_CURRENT_WAL_WRITER,
              MetricsKey.GATEWAY_WAL_WRITER_DUTY_CYCLE);
        this.wal = Storage.self().gatewaySenderWal();
        this.ringBuffer = ringBuffer;
        this.poller = ringBuffer.newPoller();
        ringBuffer.addGatingSequences(poller.getSequence());
    }

    @PostConstruct @Override public void start() { super.start(); }
    @PreDestroy @Override public void stop() { super.stop(); }

    @Override
    protected void onStart() {
        PreTouchUtil.touchDirectory(new java.io.File(ChronicleMapEnum.WAL_BASE_DIR));
        this.appender = wal.acquireAppender();
        log.info("[WAL-WRITER] 初始化完成，BinaryWire 格式寫入");
    }

    @Override
    protected int doWork() {
        int[] count = {0};
        try {
            poller.poll((event, sequence, endOfBatch) -> {
                writeToWal(event);
                count[0]++;
                return true;
            });
        } catch (Exception e) {
            log.error("[WAL-WRITER] 寫入失敗", e);
        }
        if (count[0] > 0) {
            localWriteCount += count[0];
        }
        return count[0];
    }

    /**
     * 以 BinaryWire 帶標籤格式寫入 Chronicle Queue。
     * 共用欄位 (msgType, gwTime, userId, timestamp) 固定寫入，
     * 業務欄位依 msgType 分別寫入對應 model 的欄位。
     */
    private void writeToWal(WalEvent e) {
        try (var dc = appender.writingDocument()) {
            Wire wire = dc.wire();
            if (wire == null) return;

            // 共用欄位
            wire.write(WalField.msgType).int32(e.msgType);
            wire.write(WalField.gwTime).int64(e.arrivalTimeNs);
            wire.write(WalField.userId).int64(e.userId);
            wire.write(WalField.timestamp).int64(e.timestamp);

            // 業務欄位
            switch (e.msgType) {
                case MsgType.ORDER_CREATE -> {
                    WalOrderCreate oc = e.orderCreate;
                    wire.write(WalField.symbolId).int32(oc.symbolId);
                    wire.write(WalField.price).int64(oc.price);
                    wire.write(WalField.qty).int64(oc.qty);
                    wire.write(WalField.side).int8(oc.side);
                    wire.write(WalField.clientOrderId).int64(oc.clientOrderId);
                }
                case MsgType.ORDER_CANCEL -> {
                    wire.write(WalField.orderId).int64(e.orderCancel.orderId);
                }
                case MsgType.DEPOSIT -> {
                    WalDeposit d = e.deposit;
                    wire.write(WalField.assetId).int32(d.assetId);
                    wire.write(WalField.amount).int64(d.amount);
                }
                // AUTH: 僅需 userId，已在共用欄位寫入
            }
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
        if (poller != null) {
            try {
                poller.poll((event, sequence, endOfBatch) -> {
                    writeToWal(event);
                    return true;
                });
            } catch (Exception ignored) {}
        }
        log.info("[WAL-WRITER] 已停止");
    }
}
