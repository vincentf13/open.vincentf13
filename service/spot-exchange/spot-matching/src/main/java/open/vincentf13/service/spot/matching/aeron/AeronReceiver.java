package open.vincentf13.service.spot.matching.aeron;

import io.aeron.FragmentAssembler;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.logbuffer.Header;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.service.spot.infra.aeron.*;
import open.vincentf13.service.spot.infra.aeron.AeronConstants.AeronState;
import open.vincentf13.service.spot.infra.util.PreTouchUtil;
import open.vincentf13.service.spot.infra.metrics.StaticMetricsHolder;
import open.vincentf13.service.spot.infra.thread.ThreadContext;
import open.vincentf13.service.spot.infra.thread.Worker;
import open.vincentf13.service.spot.infra.util.Clock;
import open.vincentf13.service.spot.matching.engine.Engine;
import open.vincentf13.service.spot.model.MsgProgress;
import net.openhft.chronicle.queue.RollCycles;
import org.agrona.DirectBuffer;
import org.springframework.stereotype.Component;

import static open.vincentf13.service.spot.infra.Constants.*;

/**
 * 撮合服務 Aeron 接收器
 *
 * 新增：線性一致性檢查 (Continuity Check)
 * 若 seq 非 lastProcessed + 1 且非合法的 cycle 邊界跳躍，
 * 判定為數據空洞 (Data Hole)，透過 RESUME 通知上游重定位 Tailer。
 */
@Slf4j
@Component
public class AeronReceiver extends Worker {
    private static final RollCycles ROLL_CYCLE = RollCycles.FAST_DAILY;

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
        PreTouchUtil.touchDirectory(new java.io.File(ChronicleMapEnum.DEFAULT_BASE_DIR));
        engine.onStart();
        subscription = AeronUtil.aeron().addSubscription(AeronChannel.MATCHING_FLOW, AeronChannel.DATA_STREAM_ID);
        controlPub = AeronUtil.aeron().addPublication(AeronChannel.REPORT_FLOW, AeronChannel.CONTROL_STREAM_ID);
        assembler = new FragmentAssembler(this::onFragment);

        progress.setLastProcessedSeq(engine.getNetworkProgress().getLastProcessedSeq());
        log.info("AeronReceiver 啟動，進度: {}，等待恢復...", progress.getLastProcessedSeq());
        sendResume();
    }

    @Override
    protected int doWork() {
        if (currentState == AeronState.SENDING && !subscription.isConnected()) currentState = AeronState.WAITING;

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
        long seq = buffer.getLong(offset + 8, java.nio.ByteOrder.LITTLE_ENDIAN);
        long last = progress.getLastProcessedSeq();

        // 1. 狀態同步
        if (currentState == AeronState.WAITING) {
            if (seq > last || last == MSG_SEQ_NONE) {
                log.info("AeronReceiver 鏈路對齊成功！開始消費，起始位點: {}", seq);
                currentState = AeronState.SENDING;
            } else {
                return;
            }
        }

        // 2. 冪等過濾 (重複指令)
        if (seq <= last) return;

        // 3. 線性一致性檢查 (Continuity Check)
        //    seq 為 Chronicle Queue index，同 cycle 內 +1 遞增；
        //    cycle 邊界時 sequence-in-cycle 歸零，須另行判斷。
        if (last != MSG_SEQ_NONE && seq != last + 1) {
            if (!isValidCycleBoundary(last, seq)) {
                log.error("數據空洞！expected={}, actual={}. 發送 RESUME 請求上游重定位。", last + 1, seq);
                StaticMetricsHolder.addCounter(MetricsKey.AERON_DROPPED_COUNT, 1);
                currentState = AeronState.WAITING;
                sendResume();
                return;
            }
        }

        // 4. 處理訊息
        engine.onAeronMessage(buffer.getInt(offset, java.nio.ByteOrder.LITTLE_ENDIAN), buffer, offset, length);
        progress.setLastProcessedSeq(seq);
        lastMsgReceivedTime = Clock.now();
        StaticMetricsHolder.addCounter(MetricsKey.AERON_RECV_COUNT, 1);
    }

    /**
     * 判斷 seq 跳躍是否為合法的 Chronicle Queue cycle 邊界。
     * 合法條件：新 cycle = 舊 cycle + 1，且新 cycle 的 sequence-in-cycle = 0。
     */
    private static boolean isValidCycleBoundary(long lastIndex, long currentIndex) {
        long lastCycle = ROLL_CYCLE.toCycle(lastIndex);
        long currCycle = ROLL_CYCLE.toCycle(currentIndex);
        long currSeqInCycle = ROLL_CYCLE.toSequenceNumber(currentIndex);
        return currCycle > lastCycle && currSeqInCycle == 0;
    }

    @Override
    protected void onStop() {
        engine.onStop();
        if (subscription != null) subscription.close();
        if (controlPub != null) controlPub.close();
        ThreadContext.cleanup();
        AeronUtil.cleanupThreadLocal();
    }
}
