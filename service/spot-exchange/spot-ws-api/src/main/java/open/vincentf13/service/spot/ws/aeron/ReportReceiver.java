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
    private FragmentAssembler assembler;

    // 預分配批次陣列：×2 因為 batch match 每個 fragment 拆成 taker+maker 兩筆 report
    private final Channel[] batchChannels = new Channel[AeronConstants.AERON_POLL_LIMIT * 2];
    private final ByteBuf[] batchBufs = new ByteBuf[AeronConstants.AERON_POLL_LIMIT * 2];
    private int batchCount = 0;

    // 多 Channel 分組用預分配結構（零分配 GC 友善）
    private final Channel[] uniqueChannels = new Channel[AeronConstants.AERON_POLL_LIMIT * 2];
    private final int[] channelCounts = new int[AeronConstants.AERON_POLL_LIMIT * 2];
    private final ByteBuf[][] channelBufs = new ByteBuf[AeronConstants.AERON_POLL_LIMIT * 2][AeronConstants.AERON_POLL_LIMIT * 2];

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
        batchCount++;

        if (matchingSendNs > 0) {
            StaticMetricsHolder.recordLatency(MetricsKey.LATENCY_REPORT_DELIVERY, System.nanoTime() - matchingSendNs);
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
            sendMerged(firstCh, batchBufs, count);
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
            channelBufs[idx][channelCounts[idx]++] = buf;
        }

        for (int i = 0; i < uniqueCount; i++) {
            Channel ch = uniqueChannels[i];
            int chCount = channelCounts[i];

            int totalLen = 0;
            for (int j = 0; j < chCount; j++) totalLen += channelBufs[i][j].readableBytes();
            ByteBuf combined = ALLOC.directBuffer(totalLen);
            for (int j = 0; j < chCount; j++) {
                combined.writeBytes(channelBufs[i][j]);
                channelBufs[i][j].release();
                channelBufs[i][j] = null;
            }
            uniqueChannels[i] = null;

            sendMerged(ch, channelBufs[i], chCount);
        }
        clearRefs(count);
    }

    /** 合併 bufs 並按 MAX_FRAME_SIZE 分片為多個 WebSocket frame 發送 */
    private static final int MAX_FRAME_SIZE = 60_000; // < 65536 WebSocket default max

    private void sendMerged(Channel ch, ByteBuf[] bufs, int count) {
        int totalLen = 0;
        for (int i = 0; i < count; i++) totalLen += bufs[i].readableBytes();

        if (totalLen <= MAX_FRAME_SIZE) {
            // 所有 report 合併為單一 frame
            ByteBuf combined = ALLOC.directBuffer(totalLen);
            for (int i = 0; i < count; i++) { combined.writeBytes(bufs[i]); bufs[i].release(); }
            ch.eventLoop().execute(() -> ch.writeAndFlush(new BinaryWebSocketFrame(combined)));
        } else {
            // 分片：每個 frame 不超過 MAX_FRAME_SIZE
            ByteBuf current = ALLOC.directBuffer(MAX_FRAME_SIZE);
            // 收集所有 frame 到陣列供 lambda 使用
            ByteBuf[] frames = new ByteBuf[(totalLen / MAX_FRAME_SIZE) + 2];
            int frameIdx = 0;
            for (int i = 0; i < count; i++) {
                int reportLen = bufs[i].readableBytes();
                if (current.readableBytes() + reportLen > MAX_FRAME_SIZE && current.readableBytes() > 0) {
                    frames[frameIdx++] = current;
                    current = ALLOC.directBuffer(MAX_FRAME_SIZE);
                }
                current.writeBytes(bufs[i]);
                bufs[i].release();
            }
            if (current.readableBytes() > 0) frames[frameIdx++] = current; else current.release();
            int total = frameIdx;
            ByteBuf[] finalFrames = new ByteBuf[total];
            System.arraycopy(frames, 0, finalFrames, 0, total);
            ch.eventLoop().execute(() -> {
                for (int i = 0; i < finalFrames.length; i++) {
                    ch.write(new BinaryWebSocketFrame(finalFrames[i]));
                }
                ch.flush();
            });
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
