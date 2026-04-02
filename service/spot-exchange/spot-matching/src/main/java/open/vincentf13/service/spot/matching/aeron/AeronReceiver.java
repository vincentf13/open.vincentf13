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
    private final ChronicleMap<Byte, MsgProgress> metadata;
    private final MsgProgress progress = new MsgProgress();

    private Subscription subscription;
    private Publication controlPub;
    private FragmentAssembler assembler;

    private AeronState currentState = AeronState.WAITING;
    private long lastResumeTime = 0;

    public AeronReceiver(Engine engine) {
        super("matching-receiver", MetricsKey.CPU_ID_AERON_RECEIVER, MetricsKey.CPU_ID_CURRENT_AERON_RECEIVER, MetricsKey.MATCHING_AERON_RECEVIER_WORKER_DUTY_CYCLE);
        this.engine = engine;
        this.metadata = Storage.self().msgProgressMetadata();
    }

    @PostConstruct @Override public void start() { super.start(); }
    @PreDestroy @Override public void stop() { super.stop(); }

    @Override
    protected void onStart() {
        engine.onStart();
        subscription = AeronClientHolder.aeron().addSubscription(AeronChannel.MATCHING_FLOW, AeronChannel.DATA_STREAM_ID);
        controlPub = AeronClientHolder.aeron().addPublication(AeronChannel.REPORT_FLOW, AeronChannel.CONTROL_STREAM_ID);
        assembler = new FragmentAssembler(this::onFragment);

        MsgProgress saved = metadata.get(MetaDataKey.MsgProgress.MATCHING_ENGINE_RECEIVE);
        progress.setLastProcessedSeq(saved != null ? saved.getLastProcessedSeq() : MSG_SEQ_NONE);
        log.info("AeronReceiver 啟動，進度: {}，等待恢復...", progress.getLastProcessedSeq());
        sendResume();
    }

    @Override
    protected int doWork() {
        if (currentState == AeronState.SENDING && !subscription.isConnected()) currentState = AeronState.WAITING;
        if (currentState == AeronState.WAITING && Clock.now() - lastResumeTime > AeronConstants.RESUME_SIGNAL_INTERVAL_MS) sendResume();

        int done = subscription.poll(assembler, AeronConstants.AERON_POLL_LIMIT);
        engine.onPollCycle(done);
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

        // 1. 處理狀態同步：如果是初次啟動或恢復中，接受任何有效的 WAL 索引
        if (currentState == AeronState.WAITING) {
            if (seq > last || last == MSG_SEQ_NONE) {
                log.info("AeronReceiver 鏈路對齊成功！開始消費，起始位點: {}", seq);
                currentState = AeronState.SENDING;
            } else {
                return; // 忽略舊數據
            }
        }

        // 2. 嚴格遞增檢查：由於是 Chronicle WAL Index，我們只要求 seq > last
        if (seq <= last) return; 

        // 3. 診斷性日誌：如果是傳統的 +1 跳號，記錄警告但繼續處理
        if (last != MSG_SEQ_NONE && seq != last + 1) {
            // 注意：Chronicle WAL 索引在跨 Cycle 時會發生巨大跳轉，這是正常現象
            if (log.isDebugEnabled()) log.debug("WAL 索引跳變: {} -> {}", last, seq);
        }

        engine.onAeronMessage(buffer.getInt(offset, java.nio.ByteOrder.LITTLE_ENDIAN), buffer, offset, length);
        progress.setLastProcessedSeq(seq);
        if (seq % AeronConstants.METADATA_FLUSH_PERIOD == 0) metadata.put(MetaDataKey.MsgProgress.MATCHING_ENGINE_RECEIVE, progress);
    }

    @Override
    protected void onStop() {
        engine.onStop();
        metadata.put(MetaDataKey.MsgProgress.MATCHING_ENGINE_RECEIVE, progress);
        if (subscription != null) subscription.close();
        if (controlPub != null) controlPub.close();
        ThreadContext.cleanup();
        AeronThreadContext.cleanup();
    }
}
