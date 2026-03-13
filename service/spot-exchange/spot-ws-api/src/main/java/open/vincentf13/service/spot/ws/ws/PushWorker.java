package open.vincentf13.service.spot.ws.ws;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.bytes.PointerBytesStore;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.wire.ReadMarshallable;
import net.openhft.chronicle.wire.WireIn;
import open.vincentf13.service.spot.infra.Worker;
import open.vincentf13.service.spot.infra.alloc.NativeUnsafeBuffer;
import open.vincentf13.service.spot.infra.alloc.ThreadContext;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.infra.alloc.SbeCodec;
import open.vincentf13.service.spot.model.command.AuthReportWal;
import open.vincentf13.service.spot.model.command.OrderMatchWal;
import open.vincentf13.service.spot.sbe.ExecutionReportDecoder;
import open.vincentf13.service.spot.ws.util.JsonUtil;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 訊息推送器 (Push Worker)
 職責：讀取回報 WAL 並異步推送到客戶端 WebSocket
 */
@Slf4j
@Component
public class PushWorker extends Worker implements ReadMarshallable {
    private final ChronicleQueue matchingToGwWal = Storage.self().matchingToGwWal();
    private final WsSessionManager sessionManager;
    private final int threadCount = Runtime.getRuntime().availableProcessors();
    private final ExecutorService[] stripedPool = new ExecutorService[threadCount];
    
    private final NativeUnsafeBuffer scratchBuffer = new NativeUnsafeBuffer(1024);

    private static final String[] ORDER_STATUS_STRINGS = {"NEW", "FILLED", "PARTIALLY_FILLED", "CANCELED", "REJECTED"};

    public PushWorker(WsSessionManager sessionManager) {
        this.sessionManager = sessionManager;
        for (int i = 0; i < threadCount; i++) {
            stripedPool[i] = Executors.newSingleThreadExecutor();
        }
    }

    @PostConstruct public void init() { start("push-worker"); }

    @Override
    protected int doWork() {
        ExcerptTailer tailer = matchingToGwWal.createTailer(); 
        return tailer.readDocument(this) ? 1 : 0;
    }

    @Override
    public void readMarshallable(WireIn wire) {
        final int msgType = wire.read(ChronicleWireKey.msgType).int32();
        ThreadContext ctx = ThreadContext.get();

        switch (msgType) {
            case MsgType.AUTH_REPORT -> {
                AuthReportWal report = ctx.getAuthReportWal();
                wire.read(ChronicleWireKey.payload).bytes(report);
                handleAuthReport(report.getUserId());
            }
            case MsgType.ORDER_ACCEPTED, MsgType.ORDER_REJECTED, MsgType.ORDER_CANCELED, MsgType.ORDER_MATCHED -> {
                OrderMatchWal report = ctx.getOrderMatchWal();
                wire.read(ChronicleWireKey.payload).bytes(report);
                handleExecutionReport(report.getPointBytesStore());
            }
        }
    }

    private void handleAuthReport(long userId) {
        log.info("用戶認證成功回報: {}", userId);
    }

    private void handleExecutionReport(PointerBytesStore pointBytesStore) {
        ExecutionReportDecoder decoder = SbeCodec.decodeExecutionReport(pointBytesStore);
        
        final long orderId = decoder.orderId();
        final long userId = decoder.userId();
        final int statusOrdinal = decoder.status().ordinal();
        final long cid = decoder.clientOrderId();

        stripedPool[(int)(userId % threadCount)].execute(() -> {
            PushEvent event = new PushEvent();
            event.setOrderId(orderId);
            event.setStatus(statusOrdinal >= 0 && statusOrdinal < ORDER_STATUS_STRINGS.length ? 
                    ORDER_STATUS_STRINGS[statusOrdinal] : "UNKNOWN");
            event.setCid(cid);
            event.setUserId(userId);

            JsonUtil.Envelope env = new JsonUtil.Envelope("execution", event);
            String json = JsonUtil.toJson(env);
            
            ByteBuf nettyBuf = PooledByteBufAllocator.DEFAULT.directBuffer();
            nettyBuf.writeBytes(json.getBytes());
            sessionManager.broadcast(userId, nettyBuf);
        });
    }

    @Data
    public static class PushEvent {
        private long userId, orderId, cid;
        private String status;
    }

    @Override
    protected void onStop() { 
        for (ExecutorService pool : stripedPool) {
            pool.execute(ThreadContext::cleanup);
            pool.shutdown();
        }
        ThreadContext.cleanup();
    }
}
