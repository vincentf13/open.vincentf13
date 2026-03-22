package open.vincentf13.service.spot.ws.aeron;

import io.aeron.Aeron;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.wire.WireIn;
import open.vincentf13.service.spot.infra.aeron.AbstractAeronSender;
import open.vincentf13.service.spot.infra.alloc.ThreadContext;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.infra.alloc.OffHeapUtil;
import open.vincentf13.service.spot.model.command.*;
import org.springframework.stereotype.Component;

import static open.vincentf13.service.spot.infra.Constants.*;
import static org.agrona.UnsafeAccess.UNSAFE;

import open.vincentf13.service.spot.infra.metrics.MetricsCollector;

/** 
 網關 Aeron 發送器 (Raw WAL 讀取版)
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

        final long addressForRead = bytes.addressForRead(bytes.readPosition());
        final int msgType = UNSAFE.getInt(addressForRead);
        
        final ThreadContext tc = ThreadContext.get();
        AbstractSbeModel cmd = switch (msgType) {
            case MsgType.AUTH         -> tc.getAuthCommand();
            case MsgType.ORDER_CREATE  -> tc.getOrderCreateCommand();
            case MsgType.ORDER_CANCEL  -> tc.getOrderCancelCommand();
            case MsgType.DEPOSIT      -> tc.getDepositCommand();
            default -> {
                log.warn("[GATEWAY-SENDER] 收到未知訊息類型: {}, 跳過此訊息", msgType);
                bytes.readSkip(remaining);
                yield null;
            }
        };

        if (cmd != null) {
            int payloadLen = (int) remaining;
            long backpressure = aeronClient.send(payloadLen, (buffer, offset) -> {
                final long aeronDstAddress = OffHeapUtil.getAddress(buffer, offset);
                // 1. 拷貝原始 SBE 數據
                UNSAFE.copyMemory(addressForRead, aeronDstAddress, (long) payloadLen);
                // 2. 覆蓋 Sequence ID (Offset 4)
                buffer.putLong(offset + AbstractSbeModel.SEQ_OFFSET, ctxSeq);
            });

            if (backpressure > 0) {
                MetricsCollector.add(MetricsKey.AERON_BACKPRESSURE, backpressure);
            }
            
            bytes.readSkip((long) payloadLen);
        }
    }
}
