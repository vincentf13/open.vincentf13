package open.vincentf13.service.spot.ws.ws;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.wire.WireIn;
import open.vincentf13.service.spot.infra.Worker;
import open.vincentf13.service.spot.infra.alloc.ThreadContext;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.model.command.*;
import open.vincentf13.service.spot.ws.util.JsonUtil;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 訊息推送器 (Push Worker)
 */
@Slf4j
@Component
public class PushWorker extends Worker {
    private final ChronicleQueue gatewayReceiverWal = Storage.self().gatewayReceiverWal();
    private final WsSessionManager sessionManager;
    private final int threadCount;
    private final ExecutorService[] stripedPool;
    
    private ExcerptTailer tailer;

    private static final String[] ORDER_STATUS_STRINGS = {"NEW", "PARTIALLY_FILLED", "FILLED", "CANCELED", "REJECTED"};

    private final net.openhft.chronicle.wire.ReadMarshallable walReader = this::onWalMessage;

    public PushWorker(WsSessionManager sessionManager) {
        this.sessionManager = sessionManager;
        this.threadCount = Runtime.getRuntime().availableProcessors();
        this.stripedPool = new ExecutorService[threadCount];
    }

    @PostConstruct public void init() { start("push-worker"); }

    @Override
    protected void onStart() {
        this.tailer = gatewayReceiverWal.createTailer();
        for (int i = 0; i < threadCount; i++) {
            final int poolIndex = i;
            stripedPool[i] = Executors.newSingleThreadExecutor(r -> new Thread(r, "push-pool-" + poolIndex));
        }
        log.info("PushWorker 啟動，執行緒池數量: {}", threadCount);
    }

    @Override
    protected int doWork() {
        return tailer.readDocument(walReader) ? 1 : 0;
    }

    private void onWalMessage(WireIn wire) {
        final int msgType = wire.read(ChronicleWireKey.msgType).int32();
        ThreadContext ctx = ThreadContext.get();

        switch (msgType) {
            case MsgType.AUTH_REPORT -> {
                AuthReport report = ctx.getAuthReport();
                wire.read(ChronicleWireKey.payload).bytes(report);
                log.info("用戶認證成功回報: {}", report.getUserId());
            }
            case MsgType.DEPOSIT_REPORT -> {
                DepositReport report = ctx.getDepositReport();
                wire.read(ChronicleWireKey.payload).bytes(report);
                handleDepositReport(report);
            }
            case MsgType.ORDER_ACCEPTED, MsgType.ORDER_REJECTED, MsgType.ORDER_CANCELED, MsgType.ORDER_MATCHED -> {
                ExecutionReport report = ctx.getExecutionReport();
                wire.read(ChronicleWireKey.payload).bytes(report);
                handleExecutionReport(report);
            }
        }
    }

    private void handleDepositReport(DepositReport report) {
        final long userId = report.getUserId();
        stripedPool[(int)(userId % threadCount)].execute(() -> {
            PushEvent event = new PushEvent();
            event.setUserId(userId);
            event.setStatus("DEPOSIT_SUCCESS");
            JsonUtil.Envelope env = new JsonUtil.Envelope("deposit", event);
            String json = JsonUtil.toJson(env);
            final ByteBuf nettyBuf = PooledByteBufAllocator.DEFAULT.directBuffer();
            nettyBuf.writeBytes(json.getBytes());
            sessionManager.sendMessage(userId, nettyBuf);
        });
    }

    private void handleExecutionReport(ExecutionReport report) {
        final long userId = report.getUserId();
        final int statusOrdinal = report.getStatus().ordinal();
        final String statusStr = (statusOrdinal >= 0 && statusOrdinal < ORDER_STATUS_STRINGS.length) ? 
                ORDER_STATUS_STRINGS[statusOrdinal] : "UNKNOWN";

        stripedPool[(int)(userId % threadCount)].execute(() -> {
            PushEvent event = new PushEvent();
            event.setOrderId(report.getOrderId());
            event.setStatus(statusStr);
            event.setCid(report.getClientOrderId());
            event.setUserId(userId);
            JsonUtil.Envelope env = new JsonUtil.Envelope("execution", event);
            String json = JsonUtil.toJson(env);
            final ByteBuf nettyBuf = PooledByteBufAllocator.DEFAULT.directBuffer();
            nettyBuf.writeBytes(json.getBytes());
            sessionManager.sendMessage(userId, nettyBuf);
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
            if (pool != null) {
                pool.execute(ThreadContext::cleanup);
                pool.shutdown();
            }
        }
        ThreadContext.cleanup();
    }
}
