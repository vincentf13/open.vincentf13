package open.vincentf13.service.spot.gateway.ws;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.map.ChronicleMap;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptTailer;
import org.agrona.concurrent.UnsafeBuffer;
import org.springframework.stereotype.Component;
import open.vincentf13.service.spot.infra.sbe.SbeCodec;
import open.vincentf13.service.spot.gateway.util.JsonUtil;
import open.vincentf13.service.spot.infra.Worker;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.infra.util.DecimalUtil;
import open.vincentf13.service.spot.model.Progress;
import open.vincentf13.service.spot.sbe.ExecutionReportDecoder;

import java.nio.ByteBuffer;
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

    private final WsHandler wsHandler;
    private ExcerptTailer tailer;
    private final Progress progress = new Progress();

    private final ExecutionReportDecoder executionDecoder = new ExecutionReportDecoder();
    private final UnsafeBuffer payloadBuffer = new UnsafeBuffer(0, 0);
    private final Bytes<ByteBuffer> reusableBytes = Bytes.elasticByteBuffer(4096);

    // 引入並發推送池：使用按 userId 分發的執行緒陣列，確保同一個用戶的回報有序發送
    private final int threadCount = Runtime.getRuntime().availableProcessors() * 2;
    private final ExecutorService[] stripedPool = new ExecutorService[threadCount];

    public PushWorker(WsHandler wsHandler) {
        this.wsHandler = wsHandler;
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
                reusableBytes.clear(); 
                wire.read(ChronicleWireKey.payload).bytes(reusableBytes);
                
                long address = reusableBytes.addressForRead(reusableBytes.readPosition());
                int totalLen = (int) reusableBytes.readRemaining();
                int offset = 0;

                while (offset < totalLen) {
                    payloadBuffer.wrap(address + offset, totalLen - offset);
                    SbeCodec.decode(payloadBuffer, 0, executionDecoder);
                    int currentMsgLen = SbeCodec.BLOCK_AND_VERSION_HEADER_SIZE + executionDecoder.encodedLength();

                    // 1. 提取資料
                    final long orderId = executionDecoder.orderId();
                    final int statusOrdinal = executionDecoder.status().value();
                    final long cid = executionDecoder.clientOrderId();
                    final long userId = executionDecoder.userId();

                    // 2. 關鍵修正：按 userId 取模分發，保證單用戶有序性
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

                        wsHandler.sendMessage(userId, outBuf);
                    });

                    offset += currentMsgLen;
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

    @Override
    protected void onStop() { 
        reusableBytes.releaseLast(); 
        for (ExecutorService pool : stripedPool) pool.shutdown();
    }
}
