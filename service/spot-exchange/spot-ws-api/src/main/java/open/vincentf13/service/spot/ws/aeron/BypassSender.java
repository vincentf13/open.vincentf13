package open.vincentf13.service.spot.ws.aeron;

import com.lmax.disruptor.EventPoller;
import com.lmax.disruptor.RingBuffer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.service.spot.infra.metrics.StaticMetricsHolder;
import open.vincentf13.service.spot.ws.wal.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import static open.vincentf13.service.spot.infra.Constants.*;

/**
 * Bypass 模式發送器 (壓測/低延遲模式)
 *
 * 跳過 WAL 持久化，Disruptor → Aeron 直送，消除 Chronicle Queue mmap 開銷。
 * 啟用方式：-Dspot.wal.bypass=true
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "spot.wal.bypass", havingValue = "true")
public class BypassSender extends GatewaySender {

    private long bypassSeq = 0;

    public BypassSender(@SuppressWarnings("unused") io.aeron.Aeron aeron, RingBuffer<WalEvent> ringBuffer) {
        super("bypass-sender",
              MetricsKey.CPU_ID_WAL_SENDER, MetricsKey.CPU_ID_CURRENT_WAL_SENDER,
              MetricsKey.GATEWAY_WAL_SENDER_DUTY_CYCLE, ringBuffer);
    }

    @PostConstruct @Override public void start() { super.start(); }
    @PreDestroy @Override public void stop() { super.stop(); }

    @Override
    protected void onSenderStart() {}

    // ===== WAITING：丟棄 (防 Disruptor 堆積) =====

    private final EventPoller.Handler<WalEvent> discardHandler = (event, sequence, endOfBatch) -> {
        pollCount++;
        return true;
    };

    @Override
    protected int drainWhileWaiting() {
        pollCount = 0;
        try { poller.poll(discardHandler); } catch (Exception ignored) {}
        return pollCount;
    }

    // ===== SENDING：Disruptor → Aeron 直送 =====

    private final EventPoller.Handler<WalEvent> bypassHandler = (event, sequence, endOfBatch) -> {
        long pollTimeNs = DIAGNOSE ? System.nanoTime() : 0;
        sendFromEvent(event, ++bypassSeq);
        StaticMetricsHolder.addCounter(MetricsKey.AERON_SEND_COUNT, 1);
        if (DIAGNOSE) recordTransportSubLatencies(event, pollTimeNs, System.nanoTime());
        pollCount++;
        return true;
    };

    @Override
    protected int processEvents() {
        pollCount = 0;
        try { poller.poll(bypassHandler); } catch (Exception e) { log.error("[BYPASS-SENDER] failed", e); }
        if (pollCount > 0) localWriteCount += pollCount;
        return pollCount;
    }

    // ===== RESUME =====

    @Override
    protected void onResume(long walIndex) {
        log.info("[BYPASS-SENDER] RESUME 握手成功，起始序號: {}", walIndex);
        bypassSeq = (walIndex == WAL_INDEX_NONE || walIndex == MSG_SEQ_NONE) ? 0 : walIndex;
    }

    @Override
    protected void onSenderStop() {
        try { poller.poll(discardHandler); } catch (Exception ignored) {}
    }
}
