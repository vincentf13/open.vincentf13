package open.vincentf13.service.spot.ws.aeron;

import io.aeron.Aeron;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.wire.WireIn;
import open.vincentf13.service.spot.infra.aeron.AbstractAeronSender;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.infra.alloc.OffHeapUtil;
import open.vincentf13.service.spot.model.command.AbstractSbeModel;
import open.vincentf13.service.spot.infra.jvm.Jvm;
import org.springframework.stereotype.Component;

import static open.vincentf13.service.spot.infra.Constants.*;
import static org.agrona.UnsafeAccess.UNSAFE;

import open.vincentf13.service.spot.infra.metrics.MetricsCollector;

/** 網關 Aeron 發送器 */
@Slf4j
@Component
public class AeronSender extends AbstractAeronSender {

    public AeronSender(Aeron aeron) {
        super(Storage.self().gatewaySenderWal());
    }

    @PostConstruct public void init() { start("gw-sender"); }

    @Override
    protected void onStart() {
        initChannels(AeronChannel.MATCHING_FLOW, AeronChannel.DATA_STREAM_ID,
                     AeronChannel.REPORT_FLOW, AeronChannel.CONTROL_STREAM_ID);
    }

    @Override
    protected void onBind(int cpuId) {
        MetricsCollector.recordCpuAffinity(MetricsKey.CPU_ID_AERON_SENDER, cpuId);
    }

    private long localBackPressure = 0;

    @Override
    protected void onHeartbeat() {
        if (localBackPressure > 0) {
            MetricsCollector.add(MetricsKey.AERON_BACKPRESSURE, localBackPressure);
            localBackPressure = 0;
        }
        MetricsCollector.set(MetricsKey.GATEWAY_JVM_USED_MB, Jvm.usedMemoryMb());
        MetricsCollector.set(MetricsKey.GATEWAY_JVM_MAX_MB, Jvm.maxMemoryMb());
        MetricsCollector.set(MetricsKey.GATEWAY_CPU_LOAD, Jvm.getAndResetDutyCycle());
    }

    @Override
    public void onWalMessage(WireIn wire) {
        final net.openhft.chronicle.bytes.Bytes<?> bytes = wire.bytes();
        final int len = (int) bytes.readRemaining();
        if (len <= 0) return;

        final long addr = bytes.addressForRead(bytes.readPosition());
        final long seq = tailer.index();

        if (send(len, (buf, off) -> {
            UNSAFE.copyMemory(addr, OffHeapUtil.getAddress(buf, off), len);
            buf.putLong(off + AbstractSbeModel.SEQ_OFFSET, seq);
        }) >= 0) bytes.readSkip(len);
        else localBackPressure++;
    }
}
