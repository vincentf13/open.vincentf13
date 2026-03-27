package open.vincentf13.service.spot.ws.aeron;

import io.aeron.Aeron;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.wire.WireIn;
import open.vincentf13.service.spot.infra.aeron.AbstractAeronSender;
import open.vincentf13.service.spot.infra.aeron.AeronUtil;
import open.vincentf13.service.spot.infra.aeron.AeronConstants.AeronState;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.infra.alloc.OffHeapUtil;
import open.vincentf13.service.spot.model.command.AbstractSbeModel;
import open.vincentf13.service.spot.infra.metrics.StaticMetricsHolder;
import open.vincentf13.service.spot.infra.metrics.WorkerMetrics;
import org.springframework.stereotype.Component;

import static open.vincentf13.service.spot.infra.Constants.*;
import static org.agrona.UnsafeAccess.UNSAFE;

@Slf4j
@Component
public class AeronSender extends AbstractAeronSender {

    private long localBackPressure = 0;

    public AeronSender(Aeron aeron) { super(Storage.self().gatewaySenderWal()); }

    @PostConstruct public void init() { workerStart("gw-sender"); }

    @PreDestroy
    public void shutdown() {
        workerStop();
    }

    @Override
    protected void onStart() {
        initAeronChannels(AeronChannel.MATCHING_FLOW, AeronChannel.DATA_STREAM_ID,
                          AeronChannel.REPORT_FLOW, AeronChannel.CONTROL_STREAM_ID);
    }

    @Override
    protected void collectMetrics() {
        if (localBackPressure > 0) { 
            StaticMetricsHolder.addCounter(MetricsKey.AERON_BACKPRESSURE, localBackPressure); 
            localBackPressure = 0; 
        }
        WorkerMetrics.reportThreadMetrics(
            MetricsKey.CPU_ID_AERON_SENDER,
            MetricsKey.CPU_ID_CURRENT_AERON_SENDER,
            MetricsKey.GATEWAY_AERON_SENDER_WORKER_DUTY_CYCLE
        );
    }

    @Override
    public void onWalMessage(WireIn wire) {
        final net.openhft.chronicle.bytes.Bytes<?> bytes = wire.bytes();
        final int len = (int) bytes.readRemaining();
        if (len <= 0) return;

        final long addr = bytes.addressForRead(bytes.readPosition());
        final long seq = tailer.index();

        while (running.get()) {
            int sendResult = AeronUtil.send(publication, len, (buf, off) -> copyWalMessage(addr, len, seq, buf, off), running);
            if (sendResult == 0) {
                bytes.readSkip(len);
                return;
            }
            if (handleSendFailure(sendResult)) {
                return;
            }
        }
    }

    private void copyWalMessage(long sourceAddress, int length, long seq, org.agrona.MutableDirectBuffer buffer, int offset) {
        UNSAFE.copyMemory(sourceAddress, OffHeapUtil.getAddress(buffer, offset), length);
        buffer.putLong(offset + AbstractSbeModel.SEQ_OFFSET, seq);
    }

    private boolean handleSendFailure(int sendResult) {
        if (sendResult == -2) {
            localBackPressure++;
            Thread.onSpinWait();
            return false;
        }
        if (sendResult == -1) {
            currentState = AeronState.WAITING;
            return true;
        }
        if (sendResult == -3) {
            throw new IllegalStateException("Aeron publication is unavailable");
        }
        return true;
    }
}
