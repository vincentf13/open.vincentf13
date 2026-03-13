package open.vincentf13.service.spot.ws.ws;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.map.ChronicleMap;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptTailer;
import open.vincentf13.service.spot.ws.util.JsonUtil;
import open.vincentf13.service.spot.infra.Worker;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.infra.sbe.SbeCodec;
import open.vincentf13.service.spot.infra.alloc.ThreadContext;
import open.vincentf13.service.spot.infra.alloc.NativeUnsafeBuffer;
import open.vincentf13.service.spot.model.Progress;
import open.vincentf13.service.spot.sbe.ExecutionReportDecoder;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 WebSocket 推送背景執行緒 (PushWorker) - 並發優化版
 職責：讀取回報流並分發至並發執行緒池進行序列化與推送，提升大行情下的吞吐量
 */
@Slf4j
@Component
public class PushWorker extends Worker {
    private final ChronicleQueue matchingToGwWal = Storage.self().matchingToGwWal();
    private final ChronicleMap<Byte, Progress> metadata = Storage.self().metadata();

    private final WsSessionManager sessionManager;
    private ExcerptTailer tailer;
    private final Progress progress = new Progress();

    private final NativeUnsafeBuffer nativeUnsafeBuffer = new NativeUnsafeBuffer(4096);

    // 引入並發推送池：使用按 userId 分發的執行緒陣列，確保同一個用戶的回報有序發送
    private final int threadCount = Runtime.getRuntime().availableProcessors() * 2;
    private final ExecutorService[] stripedPool = new ExecutorService[threadCount];

    public PushWorker(WsSessionManager sessionManager) {
        this.sessionManager = sessionManager;
        for (int i = 0; i < threadCount; i++) {
            stripedPool[i] = Executors.newSingleThreadExecutor();
        }
    }

    @PostConstruct public void init() { start("gw-push-worker"); }
    
    @Override
    protected void onStart() {
        this.tailer = matchingToGwWal.createTailer();
        Progress saved = metadata.get(MetaDataKey.WS_PUSH_TO_CLIENT_POINT);
        if (saved != null) {
            progress.setLastProcessedSeq(saved.getLastProcessedSeq());
            tailer.moveToIndex(progress.getLastProcessedSeq());
        } else progress.setLastProcessedSeq(-1L);
    }

    @Data
    private static class PushEvent {
        long orderId;
        String status;
        long cid;
        long userId;
    }

    @Override
    protected int doWork() {
        int workCount = 0;
        // 批量讀取優化：一次最多處理 100 筆
        for (int i = 0; i < 100; i++) {
            boolean handled = tailer.readDocument(wire -> {
                long seq = tailer.index();
                int msgType = wire.read(ChronicleWireKey.msgType).int32();
                nativeUnsafeBuffer.clear(); 
                wire.read(ChronicleWireKey.payload).bytes(nativeUnsafeBuffer.bytes());

                if (msgType == MsgType.EXECUTION_REPORT) {
                    handleExecutionReport(nativeUnsafeBuffer.bytes());
                } else if (msgType == MsgType.AUTH_REPORT) {
                    handleAuthReport(nativeUnsafeBuffer.bytes());
                }

                progress.setLastProcessedSeq(seq);
                if (seq % 100 == 0) {
                    metadata.put(MetaDataKey.WS_PUSH_TO_CLIENT_POINT, progress);
                }
            });
            
            if (handled) workCount++;
            else break;
        }
        return workCount;
    }

    private void handleExecutionReport(Bytes<?> bytes) {
        long address = bytes.addressForRead(bytes.readPosition());
        int totalLen = (int) bytes.readRemaining();
        int offset = 0;

        while (offset < totalLen) {
            nativeUnsafeBuffer.wrap(address + offset, totalLen - offset);
            ExecutionReportDecoder executionDecoder = ThreadContext.get().getExecutionReportDecoder();
            SbeCodec.decode(nativeUnsafeBuffer.buffer(), 0, executionDecoder);
            int currentMsgLen = SbeCodec.BLOCK_AND_VERSION_HEADER_SIZE + executionDecoder.encodedLength();

            final long orderId = executionDecoder.orderId();
            final int statusOrdinal = executionDecoder.status().value();
            final long cid = executionDecoder.clientOrderId();
            final long userId = executionDecoder.userId();

            stripedPool[(int)(userId % threadCount)].execute(() -> {
                PushEvent event = new PushEvent();
                event.setOrderId(orderId);
                event.setStatus(statusOrdinal >= 0 && statusOrdinal < ORDER_STATUS_STRINGS.length ? 
                        ORDER_STATUS_STRINGS[statusOrdinal] : "UNKNOWN");
                event.setCid(cid);
                event.setUserId(userId);

                JsonUtil.Envelope env = new JsonUtil.Envelope("execution", event);
                ByteBuf outBuf = PooledByteBufAllocator.DEFAULT.buffer(512);
                JsonUtil.writeToByteBuf(outBuf, env);
                sessionManager.sendMessage(userId, outBuf);
            });

            offset += currentMsgLen;
        }
    }

    private void handleAuthReport(Bytes<?> bytes) {
        if (bytes.readRemaining() < 8) return;
        final long userId = bytes.readLong();
        
        stripedPool[(int)(userId % threadCount)].execute(() -> {
            JsonUtil.Envelope env = new JsonUtil.Envelope("auth", "success");
            ByteBuf outBuf = PooledByteBufAllocator.DEFAULT.buffer(128);
            JsonUtil.writeToByteBuf(outBuf, env);
            sessionManager.sendMessage(userId, outBuf);
        });
    }

    @Override
    protected void onStop() { 
        for (ExecutorService pool : stripedPool) {
            pool.execute(ThreadContext::cleanup);
            pool.shutdown();
        }
        ThreadContext.cleanup(); // 清理 Worker 自身執行緒
    }
}
