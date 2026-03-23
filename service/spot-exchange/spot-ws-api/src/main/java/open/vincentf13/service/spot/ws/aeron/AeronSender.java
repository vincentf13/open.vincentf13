package open.vincentf13.service.spot.ws.aeron;

import io.aeron.Aeron;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.wire.WireIn;
import open.vincentf13.service.spot.infra.aeron.AbstractAeronSender;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.infra.alloc.OffHeapUtil;
import open.vincentf13.service.spot.model.command.AbstractSbeModel;
import org.springframework.stereotype.Component;

import static open.vincentf13.service.spot.infra.Constants.*;
import static org.agrona.UnsafeAccess.UNSAFE;

import open.vincentf13.service.spot.infra.metrics.MetricsCollector;

/** 
 網關 Aeron 發送器 (Raw WAL 讀取版)
 優化：極簡化指針直寫，移除 SBE 模型解碼分支
 */
@Slf4j
@Component
public class AeronSender extends AbstractAeronSender {

    public AeronSender(Aeron aeron) {
        super(aeron, Storage.self().gatewaySenderWal(), 
              AeronChannel.MATCHING_FLOW, AeronChannel.DATA_STREAM_ID,
              AeronChannel.REPORT_FLOW, AeronChannel.CONTROL_STREAM_ID);
    }

    @PostConstruct public void init() { start("gw-command-sender"); }

    @Override
    protected void onBind(int cpuId) {
        MetricsCollector.recordCpuAffinity(MetricsKey.CPU_ID_AERON_SENDER, cpuId);
    }

    private long lastMetricsTime = 0;

    @Override
    protected int doWork() {
        int work = super.doWork();
        long now = System.currentTimeMillis() / 1000;
        if (now > lastMetricsTime) {
            updateProcessMetrics();
            lastMetricsTime = now;
        }
        return work;
    }

    private void updateProcessMetrics() {
        Runtime r = Runtime.getRuntime();
        MetricsCollector.set(MetricsKey.GATEWAY_JVM_USED_MB, (r.totalMemory() - r.freeMemory()) / 1024 / 1024);
        MetricsCollector.set(MetricsKey.GATEWAY_JVM_MAX_MB, r.maxMemory() / 1024 / 1024);
        
        java.lang.management.OperatingSystemMXBean osBean = java.lang.management.ManagementFactory.getOperatingSystemMXBean();
        if (osBean instanceof com.sun.management.OperatingSystemMXBean sunOsBean) {
            MetricsCollector.set(MetricsKey.GATEWAY_CPU_LOAD, (long)(sunOsBean.getCpuLoad() * 100));
        }
    }

    @Override
    public void onWalMessage(WireIn wire) {
        final long ctxSeq = tailer.index();
        final Bytes<?> bytes = wire.bytes();
        
        long remaining = bytes.readRemaining();
        if (remaining <= 0) return;

        // 核心優化：零解碼直接拷貝發送
        // WAL 中的數據已經是 SBE 格式，直接定位指針
        final long addressForRead = bytes.addressForRead(bytes.readPosition());
        final int payloadLen = (int) remaining;

        long backpressure = aeronClient.send(payloadLen, (buffer, offset) -> {
            final long aeronDstAddress = OffHeapUtil.getAddress(buffer, offset);
            // 1. 物理記憶體零拷貝 (WAL -> Aeron)
            UNSAFE.copyMemory(addressForRead, aeronDstAddress, (long) payloadLen);
            // 2. 注入唯一 Sequence ID (位於 SBE Header 固定偏移量)
            buffer.putLong(offset + AbstractSbeModel.SEQ_OFFSET, ctxSeq);
        });

        if (backpressure > 0) {
            MetricsCollector.add(MetricsKey.AERON_BACKPRESSURE, backpressure);
        }
        
        bytes.readSkip((long) payloadLen);
    }
}
