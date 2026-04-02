package open.vincentf13.service.spot.matching.aeron;

import io.aeron.FragmentAssembler;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.logbuffer.Header;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.map.ChronicleMap;
import open.vincentf13.service.spot.infra.aeron.*;
import open.vincentf13.service.spot.infra.aeron.AeronConstants.AeronState;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.infra.metrics.StaticMetricsHolder;
import open.vincentf13.service.spot.infra.metrics.WorkerMetrics;
import open.vincentf13.service.spot.infra.thread.ThreadContext;
import open.vincentf13.service.spot.infra.thread.Worker;
import open.vincentf13.service.spot.infra.util.Clock;
import open.vincentf13.service.spot.matching.engine.Engine;
import open.vincentf13.service.spot.model.MsgProgress;
import org.agrona.DirectBuffer;
import org.springframework.stereotype.Component;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 撮合服務 Aeron 接收器 (極簡扁平化版) */
@Slf4j
@Component
public class AeronReceiver extends Worker {
    private final Engine engine;
    private final MsgProgress progress = new MsgProgress();

    private Subscription subscription;
    private Publication controlPub;
    private FragmentAssembler assembler;

    private AeronState currentState = AeronState.WAITING;
    private long lastResumeTime = 0;
    private long lastMsgReceivedTime = 0;

    public AeronReceiver(Engine engine) {
        super("matching-receiver", MetricsKey.CPU_ID_AERON_RECEIVER, MetricsKey.CPU_ID_CURRENT_AERON_RECEIVER, MetricsKey.MATCHING_AERON_RECEVIER_WORKER_DUTY_CYCLE);
        this.engine = engine;
    }

    @PostConstruct @Override public void start() { super.start(); }
    @PreDestroy @Override public void stop() { super.stop(); }

    @Override
    protected void onStart() {
        engine.onStart();
        subscription = AeronClientHolder.aeron().addSubscription(AeronChannel.MATCHING_FLOW, AeronChannel.DATA_STREAM_ID);
        controlPub = AeronClientHolder.aeron().addPublication(AeronChannel.REPORT_FLOW, AeronChannel.CONTROL_STREAM_ID);
        assembler = new FragmentAssembler(this::onFragment);

        // 從 Engine 讀取恢復位點 (Engine 啟動時已載入網路進度)
        progress.setLastProcessedSeq(engine.getNetworkProgress().getLastProcessedSeq());
        log.info("AeronReceiver 啟動，進度: {}，等待恢復...", progress.getLastProcessedSeq());
        sendResume();
    }

    @Override
    protected int doWork() {
        if (currentState == AeronState.SENDING && !subscription.isConnected()) currentState = AeronState.WAITING;

        // 死鎖防護：SENDING 狀態下長時間無新消息 → 懷疑 Sender 卡在 WAITING（SEND_DISCONNECTED 後無 RESUME）
        // 重置為 WAITING 並重新發送 RESUME，打破死鎖
        if (currentState == AeronState.SENDING
                && lastMsgReceivedTime > 0
                && Clock.now() - lastMsgReceivedTime > AeronConstants.RECEIVER_STALL_TIMEOUT_MS) {
            log.warn("AeronReceiver 超時 {}ms 未收到數據，重置為 WAITING 以重發 RESUME", AeronConstants.RECEIVER_STALL_TIMEOUT_MS);
            currentState = AeronState.WAITING;
        }

        if (currentState == AeronState.WAITING && Clock.now() - lastResumeTime > AeronConstants.RESUME_SIGNAL_INTERVAL_MS) sendResume();

        int done = subscription.poll(assembler, AeronConstants.AERON_POLL_LIMIT);
        engine.onPollCycle(done, progress.getLastProcessedSeq());
        return done;
    }

    private void sendResume() {
        AeronUtil.send(controlPub, AeronConstants.RESUME_SIGNAL_LENGTH, (buffer, offset) -> {
            buffer.putInt(offset, MsgType.RESUME, java.nio.ByteOrder.LITTLE_ENDIAN);
            buffer.putLong(offset + AeronConstants.MSG_SEQ_OFFSET, progress.getLastProcessedSeq(), java.nio.ByteOrder.LITTLE_ENDIAN);
        });
        lastResumeTime = Clock.now();
    }

    private void onFragment(DirectBuffer buffer, int offset, int length, Header header) {
        // 強制使用 8 偏移量，確保與 32-byte 標頭結構對齊
        long seq = buffer.getLong(offset + 8, java.nio.ByteOrder.LITTLE_ENDIAN); 
        long last = progress.getLastProcessedSeq();

        // 1. 處理狀態同步
        if (currentState == AeronState.WAITING) {
            if (seq > last || last == MSG_SEQ_NONE) {
                log.info("AeronReceiver 鏈路對齊成功！開始消費，起始位點: {}", seq);
                currentState = AeronState.SENDING;
            } else {
                return;
            }
        }

        if (seq <= last) return;

        engine.onAeronMessage(buffer.getInt(offset, java.nio.ByteOrder.LITTLE_ENDIAN), buffer, offset, length);
        progress.setLastProcessedSeq(seq);
        lastMsgReceivedTime = Clock.now();
        StaticMetricsHolder.addCounter(MetricsKey.AERON_RECV_COUNT, 1);
    }

    @Override
    protected void onStop() {
        engine.onStop();
        if (subscription != null) subscription.close();
        if (controlPub != null) controlPub.close();
        ThreadContext.cleanup();
        AeronThreadContext.cleanup();
    }
}
