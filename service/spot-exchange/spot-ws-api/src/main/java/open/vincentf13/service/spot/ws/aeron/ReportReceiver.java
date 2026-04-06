package open.vincentf13.service.spot.ws.aeron;

import io.aeron.Aeron;
import io.aeron.FragmentAssembler;
import io.aeron.Subscription;
import io.aeron.logbuffer.Header;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.service.spot.infra.aeron.AeronConstants;
import open.vincentf13.service.spot.infra.aeron.AeronUtil;
import open.vincentf13.service.spot.infra.metrics.StaticMetricsHolder;
import open.vincentf13.service.spot.infra.thread.Worker;
import open.vincentf13.service.spot.ws.ws.WsSessionManager;
import org.agrona.DirectBuffer;
import org.springframework.stereotype.Component;

import java.nio.ByteOrder;

import static open.vincentf13.service.spot.infra.Constants.*;

/**
 * 回報接收器 (Report Receiver)
 *
 * 訂閱 Matching Engine 的 Aeron 回報流，
 * 將 SBE 編碼的執行回報原樣轉發至對應用戶的 WebSocket 連線。
 *
 * 優化策略：每輪 poll 收集所有回報到預分配陣列，按 Channel 分組後
 * 提交單次 EventLoop task 批量寫入，避免 per-message 的跨線程 WriteTask 分配。
 */
@Slf4j
@Component
public class ReportReceiver extends Worker {

    private static final int USER_ID_OFFSET = 28;
    private static final PooledByteBufAllocator ALLOC = PooledByteBufAllocator.DEFAULT;

    private final WsSessionManager sessionManager;
    private Subscription subscription;
    private FragmentAssembler assembler;

    // 預分配批次陣列，避免 per-message 的 ArrayList/record 分配
    private final Channel[] batchChannels = new Channel[AeronConstants.AERON_POLL_LIMIT];
    private final ByteBuf[] batchBufs = new ByteBuf[AeronConstants.AERON_POLL_LIMIT];
    private int batchCount = 0;

    public ReportReceiver(@SuppressWarnings("unused") Aeron aeron, WsSessionManager sessionManager) {
        super("report-receiver",
              MetricsKey.CPU_ID_REPORT_RECEIVER, MetricsKey.CPU_ID_CURRENT_REPORT_RECEIVER,
              MetricsKey.GATEWAY_REPORT_RECEIVER_DUTY_CYCLE);
        this.sessionManager = sessionManager;
    }

    @PostConstruct @Override public void start() { super.start(); }
    @PreDestroy @Override public void stop() { super.stop(); }

    @Override
    protected void onStart() {
        subscription = AeronUtil.aeron().addSubscription(AeronChannel.REPORT_FLOW, AeronChannel.REPORT_STREAM_ID);
        assembler = new FragmentAssembler(this::onReport);
        log.info("ReportReceiver 已啟動，訂閱回報流");
    }

    @Override
    protected int doWork() {
        int work = subscription.poll(assembler, AeronConstants.AERON_POLL_LIMIT);
        if (batchCount > 0) {
            flushBatch();
        }
        return work;
    }

    private static final int MATCHING_END_NS_OFFSET = 4; // report header offset 4-11: T_send nanoTime

    private void onReport(DirectBuffer buffer, int offset, int length, Header header) {
        if (length < USER_ID_OFFSET + 8) return;
        long userId = buffer.getLong(offset + USER_ID_OFFSET, ByteOrder.LITTLE_ENDIAN);
        StaticMetricsHolder.addCounter(MetricsKey.REPORT_RECV_COUNT, 1);

        // report_delivery 延遲：T_send (Matching writeFrameHeader) → now (GW receive)
        long matchingSendNs = buffer.getLong(offset + MATCHING_END_NS_OFFSET, ByteOrder.LITTLE_ENDIAN);
        if (matchingSendNs > 0) {
            StaticMetricsHolder.recordLatency(MetricsKey.LATENCY_REPORT_DELIVERY, System.nanoTime() - matchingSendNs);
        }

        Channel ch = sessionManager.findChannel(userId);
        if (ch == null || !ch.isActive()) return;

        ByteBuf nettyBuf = ALLOC.directBuffer(length, length);
        buffer.getBytes(offset, nettyBuf.nioBuffer(0, length), length);
        nettyBuf.writerIndex(length);

        batchChannels[batchCount] = ch;
        batchBufs[batchCount] = nettyBuf;
        batchCount++;
    }

    /**
     * 單 Channel 快速路徑：1 次 eventLoop.execute() 批量寫入所有回報。
     * 多 Channel 路徑：每 Channel 1 次 execute()。
     * 原先 N 次跨線程 WriteTask + ChannelPromise 降為 ≤channelCount 次。
     */
    private void flushBatch() {
        int count = batchCount;
        batchCount = 0;

        // 快速路徑：所有回報都給同一個 Channel（壓測典型場景）
        Channel firstCh = batchChannels[0];
        boolean singleChannel = true;
        for (int i = 1; i < count; i++) {
            if (batchChannels[i] != firstCh) { singleChannel = false; break; }
        }

        if (singleChannel) {
            // 複製 buf 參考到局部陣列，避免 lambda 捕獲 this 的可變欄位
            // 注意：不能用預分配 field array，因為 EventLoop 線程和 report-receiver 線程有 race
            ByteBuf[] bufs = new ByteBuf[count];
            System.arraycopy(batchBufs, 0, bufs, 0, count);
            clearRefs(count);
            firstCh.eventLoop().execute(() -> {
                for (ByteBuf buf : bufs) firstCh.write(new BinaryWebSocketFrame(buf));
                firstCh.flush();
            });
            return;
        }

        // 多 Channel 路徑：逐條 writeAndFlush
        for (int i = 0; i < count; i++) {
            Channel ch = batchChannels[i];
            ByteBuf buf = batchBufs[i];
            if (ch.isActive()) {
                ch.eventLoop().execute(() -> ch.writeAndFlush(new BinaryWebSocketFrame(buf)));
            } else {
                buf.release();
            }
        }
        clearRefs(count);
    }

    private void clearRefs(int count) {
        for (int i = 0; i < count; i++) {
            batchChannels[i] = null;
            batchBufs[i] = null;
        }
    }

    @Override
    protected void onStop() {
        if (subscription != null) subscription.close();
    }
}
