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

    private long localBackPressure = 0;

    @Override
    protected int doWork() {
        int work = super.doWork();
        
        // 批量匯總背壓指標
        if (localBackPressure > 0) {
            MetricsCollector.add(MetricsKey.AERON_BACKPRESSURE, localBackPressure);
            localBackPressure = 0;
        }

        long now = open.vincentf13.service.spot.infra.util.Clock.now() / 1000;
        if (now > lastMetricsTime) {
            updateProcessMetrics();
            lastMetricsTime = now;
        }
        return work;
    }

    @Override
    public void onWalMessage(WireIn wire) {
        final net.openhft.chronicle.bytes.Bytes<?> bytes = wire.bytes();
        final int payloadLen = (int) bytes.readRemaining();
        if (payloadLen <= 0) return;

        final long addressForRead = bytes.addressForRead(bytes.readPosition());
        final long ctxSeq = tailer.index();

        // 核心修正：檢查發送結果
        long result = aeronClient.send(payloadLen, (buffer, offset) -> {
            UNSAFE.copyMemory(addressForRead, OffHeapUtil.getAddress(buffer, offset), (long) payloadLen);
            buffer.putLong(offset + AbstractSbeModel.SEQ_OFFSET, ctxSeq);
        });

        if (result >= 0) {
            bytes.readSkip((long) payloadLen);
        } else {
            localBackPressure++;
        }
    }
}
