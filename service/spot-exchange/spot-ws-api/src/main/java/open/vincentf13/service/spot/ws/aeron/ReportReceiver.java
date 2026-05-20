package open.vincentf13.service.spot.ws.aeron;

import io.aeron.Aeron;
import io.aeron.Subscription;
import io.aeron.logbuffer.FragmentHandler;
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
import open.vincentf13.service.spot.sbe.*;
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
    private FragmentHandler fragmentHandler;

    // 預分配批次陣列：×2 因為 batch match 每個 fragment 拆成 taker+maker 兩筆 report
    private final Channel[] batchChannels = new Channel[AeronConstants.AERON_POLL_LIMIT * 2];
    private final ByteBuf[] batchBufs = new ByteBuf[AeronConstants.AERON_POLL_LIMIT * 2];
    private final long[] batchEntryNs = new long[AeronConstants.AERON_POLL_LIMIT * 2]; // per-report entry ts，用於 fan-out 量測
    private int batchCount = 0;

    // ===== fan-out 量測：預配 FanoutSlot 池，避免 lambda / long[] 每批分配 =====
    // ChannelFuture listener 在 channel EventLoop 觸發；ReportReceiver 寫 → addListener 提供 happens-before
    // ring size 設大於最大 in-flight (每 channel 約 < 10 個未完 future)，4 channel × 10 = 40 → 256 安全
    private static final int FANOUT_RING_MASK = 0xFF; // 256 slots
    private final FanoutSlot[] fanoutRing = new FanoutSlot[FANOUT_RING_MASK + 1];
    private int fanoutRingIdx = 0;

    {
        for (int i = 0; i < fanoutRing.length; i++) fanoutRing[i] = new FanoutSlot();
    }

    /** 預配 listener 物件，addListener 不分配 lambda；operationComplete 不分配陣列 */
    private static final class FanoutSlot implements io.netty.util.concurrent.GenericFutureListener<io.netty.channel.ChannelFuture> {
        final long[] tsArray = new long[AeronConstants.AERON_POLL_LIMIT * 2];
        int count = 0;
        @Override public void operationComplete(io.netty.channel.ChannelFuture future) {
            long completeNs = System.nanoTime();
            int n = count;
            long[] ts = tsArray;
            for (int i = 0; i < n; i++) {
                StaticMetricsHolder.recordLatency(MetricsKey.LATENCY_FANOUT, completeNs - ts[i]);
            }
            count = 0;
        }
    }

    // 多 Channel 分組用預分配結構（零分配 GC 友善）
    private final Channel[] uniqueChannels = new Channel[AeronConstants.AERON_POLL_LIMIT * 2];
    private final int[] channelCounts = new int[AeronConstants.AERON_POLL_LIMIT * 2];
    private final ByteBuf[][] channelBufs = new ByteBuf[AeronConstants.AERON_POLL_LIMIT * 2][AeronConstants.AERON_POLL_LIMIT * 2];
    private final long[][] channelEntryNs = new long[AeronConstants.AERON_POLL_LIMIT * 2][AeronConstants.AERON_POLL_LIMIT * 2];

    public ReportReceiver(@SuppressWarnings("unused") Aeron aeron,
                          WsSessionManager sessionManager,
                          @SuppressWarnings("unused") GatewaySender gatewaySender) {
        super("gateway-receiver",
              MetricsKey.CPU_ID_REPORT_RECEIVER, MetricsKey.CPU_ID_CURRENT_REPORT_RECEIVER,
              MetricsKey.GATEWAY_REPORT_RECEIVER_DUTY_CYCLE);
        // gatewaySender 注入只是為了強制 @PostConstruct 順序：
        // GatewaySender (或 WalSender) 先 bind 到 P-core pool 第一個 slot，
        // ReportReceiver 再 bind 到第二個 slot。確保 WAL 模式下 WalSender 拿 P1。
        this.sessionManager = sessionManager;
    }

    @PostConstruct @Override public void start() { super.start(); }
    @PreDestroy @Override public void stop() { super.stop(); }

    @Override
    protected void onStart() {
        subscription = AeronUtil.aeron().addSubscription(AeronChannel.REPORT_FLOW, AeronChannel.REPORT_STREAM_ID);
        // reports are always single-fragment (<100B), skip FragmentAssembler overhead
        fragmentHandler = this::onReport;
        log.info("ReportReceiver 已啟動，訂閱回報流");
    }

    @Override
    protected int doWork() {
        int work = subscription.poll(fragmentHandler, AeronConstants.AERON_POLL_LIMIT);
        if (batchCount > 0) {
            flushBatch();
        }
        return work;
    }

    private static final int MATCHING_END_NS_OFFSET = 4; // report header offset 4-11: T_send nanoTime
    private static final int REPORT_HEADER_SIZE = 20;   // ExecutionReporter.HEADER_SIZE

    // 單個 report 長度查表（HEADER_SIZE + SBE BLOCK_LENGTH，引用 SBE 生成常數避免硬編碼錯誤）
    private static final int ACCEPTED_LEN = REPORT_HEADER_SIZE + OrderAcceptedEncoder.BLOCK_LENGTH;  // 20+32=52
    private static final int REJECTED_LEN = REPORT_HEADER_SIZE + OrderRejectedEncoder.BLOCK_LENGTH;  // 20+24=44
    private static final int MATCHED_LEN  = REPORT_HEADER_SIZE + OrderMatchedEncoder.BLOCK_LENGTH;   // 20+65=85
    private static final int CANCELED_LEN = REPORT_HEADER_SIZE + OrderCanceledEncoder.BLOCK_LENGTH;  // 20+40=60

    private void onReport(DirectBuffer buffer, int offset, int length, Header header) {
        // 支援 batch message：一個 Aeron fragment 可能包含多個 report（match taker + maker）
        int pos = offset;
        int end = offset + length;
        while (pos + REPORT_HEADER_SIZE <= end) {
            int reportLen = getSingleReportLength(buffer, pos);
            if (pos + reportLen > end || reportLen < USER_ID_OFFSET + 8) break;
            processOneReport(buffer, pos, reportLen);
            pos += reportLen;
        }
    }

    private static int getSingleReportLength(DirectBuffer buffer, int pos) {
        int msgType = buffer.getInt(pos, ByteOrder.LITTLE_ENDIAN);
        return switch (msgType) {
            case MsgType.ORDER_ACCEPTED -> ACCEPTED_LEN;
            case MsgType.ORDER_REJECTED -> REJECTED_LEN;
            case MsgType.ORDER_MATCHED  -> MATCHED_LEN;
            case MsgType.ORDER_CANCELED -> CANCELED_LEN;
            default -> Integer.MAX_VALUE; // 未知類型，停止解析
        };
    }

    private void processOneReport(DirectBuffer buffer, int offset, int length) {
        long entryNs = System.nanoTime();
        long userId = buffer.getLong(offset + USER_ID_OFFSET, ByteOrder.LITTLE_ENDIAN);
        long matchingSendNs = buffer.getLong(offset + MATCHING_END_NS_OFFSET, ByteOrder.LITTLE_ENDIAN);
        StaticMetricsHolder.addCounter(MetricsKey.REPORT_RECV_COUNT, 1);

        Channel ch = sessionManager.findChannel(userId);
        if (ch == null || !ch.isActive()) return;

        ByteBuf nettyBuf = ALLOC.directBuffer(length, length);
        buffer.getBytes(offset, nettyBuf.nioBuffer(0, length), length);
        nettyBuf.writerIndex(length);

        batchChannels[batchCount] = ch;
        batchBufs[batchCount] = nettyBuf;
        batchEntryNs[batchCount] = entryNs;
        batchCount++;

        if (matchingSendNs > 0) {
            StaticMetricsHolder.recordLatency(MetricsKey.LATENCY_REPORT_DELIVERY, entryNs - matchingSendNs);
        }
    }

    /**
     * 將本輪 poll 的所有回報按 Channel 分組，每個 Channel 合併為一個 WebSocket frame 發送。
     * 相比原先 N 個 frame，減少 WebSocket 編碼開銷和 Netty EventLoop write 次數。
     * BenchmarkTool 端需支援從單個 frame 中解析多筆 report。
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
            // 合併 reports 到 ByteBuf，按 MAX_FRAME_SIZE 分片發送
            sendMerged(firstCh, batchBufs, batchEntryNs, count);
            clearRefs(count);
            return;
        }

        // 多 Channel 路徑：每 Channel 合併為一個 frame
        int uniqueCount = 0;
        for (int i = 0; i < count; i++) {
            Channel ch = batchChannels[i];
            ByteBuf buf = batchBufs[i];
            if (ch == null || !ch.isActive()) {
                if (buf != null) buf.release();
                continue;
            }

            int idx = -1;
            for (int j = 0; j < uniqueCount; j++) {
                if (uniqueChannels[j] == ch) { idx = j; break; }
            }
            if (idx == -1) {
                idx = uniqueCount++;
                uniqueChannels[idx] = ch;
                channelCounts[idx] = 0;
            }
            int slot = channelCounts[idx]++;
            channelBufs[idx][slot] = buf;
            channelEntryNs[idx][slot] = batchEntryNs[i];
        }

        // 每個 unique channel：直接把該 channel 的 bufs 交給 sendMerged 合併並送出（含 release）
        for (int i = 0; i < uniqueCount; i++) {
            Channel ch = uniqueChannels[i];
            int chCount = channelCounts[i];
            sendMerged(ch, channelBufs[i], channelEntryNs[i], chCount);
            // 清陣列 slot（sendMerged 已 release buf；這裡只清引用避免持參）
            for (int j = 0; j < chCount; j++) channelBufs[i][j] = null;
            uniqueChannels[i] = null;
        }
        clearRefs(count);
    }

    /** 合併 bufs 並按 MAX_FRAME_SIZE 分片為多個 WebSocket frame 發送 */
    private static final int MAX_FRAME_SIZE = 60_000; // < 65536 WebSocket default max

    private void sendMerged(Channel ch, ByteBuf[] bufs, long[] entryNs, int count) {
        int totalLen = 0;
        for (int i = 0; i < count; i++) totalLen += bufs[i].readableBytes();

        // 取下一個預配 FanoutSlot，把 entryNs 複製進預配陣列；無分配
        FanoutSlot slot = fanoutRing[fanoutRingIdx = (fanoutRingIdx + 1) & FANOUT_RING_MASK];
        System.arraycopy(entryNs, 0, slot.tsArray, 0, count);
        slot.count = count;

        if (totalLen <= MAX_FRAME_SIZE) {
            ByteBuf combined = ALLOC.directBuffer(totalLen);
            for (int i = 0; i < count; i++) { combined.writeBytes(bufs[i]); bufs[i].release(); }
            ch.writeAndFlush(new BinaryWebSocketFrame(combined)).addListener(slot);
        } else {
            // 分片：每個 frame 不超過 MAX_FRAME_SIZE，逐一 write，最後一片 flush 並附 listener
            ByteBuf current = ALLOC.directBuffer(MAX_FRAME_SIZE);
            for (int i = 0; i < count; i++) {
                int reportLen = bufs[i].readableBytes();
                if (current.readableBytes() + reportLen > MAX_FRAME_SIZE && current.readableBytes() > 0) {
                    ch.write(new BinaryWebSocketFrame(current));
                    current = ALLOC.directBuffer(MAX_FRAME_SIZE);
                }
                current.writeBytes(bufs[i]);
                bufs[i].release();
            }
            if (current.readableBytes() > 0) {
                ch.writeAndFlush(new BinaryWebSocketFrame(current)).addListener(slot);
            } else {
                current.release();
                ch.flush();
                slot.count = 0; // 沒實際發送，跳過量測
            }
        }
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
