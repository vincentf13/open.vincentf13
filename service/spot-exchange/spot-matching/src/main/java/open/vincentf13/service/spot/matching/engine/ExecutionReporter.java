package open.vincentf13.service.spot.matching.engine;

import io.aeron.Publication;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.service.spot.infra.aeron.AeronUtil;
import open.vincentf13.service.spot.infra.metrics.StaticMetricsHolder;
import open.vincentf13.service.spot.model.Order;
import open.vincentf13.service.spot.model.Trade;
import open.vincentf13.service.spot.model.command.AbstractSbeModel;
import open.vincentf13.service.spot.sbe.*;
import org.springframework.stereotype.Component;

import java.nio.ByteOrder;

import static open.vincentf13.service.spot.infra.Constants.*;
import static open.vincentf13.service.spot.infra.aeron.AeronUtil.SEND_OK;

/**
 * 執行回報器 (Execution Reporter)
 *
 * 將撮合結果以 SBE 編碼透過 Aeron 發送至 Gateway，
 * 由 ReportReceiver 接收後推送至 WebSocket 客戶端。
 *
 * 回報格式與指令格式對稱：[20B header + SBE body]
 * Header: [0-3] MsgType | [4-11] reserved | [12-19] SBE message header
 */
@Slf4j
@Component
public class ExecutionReporter implements AutoCloseable {

    private static final int HEADER_SIZE = 20; // 與 client frame 格式對稱
    private static final int SBE_HEADER_OFFSET = 12;

    private Publication publication;

    // SBE 編碼器 (單線程，matching thread 獨佔)
    private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    private final OrderAcceptedEncoder acceptedEncoder = new OrderAcceptedEncoder();
    private final OrderRejectedEncoder rejectedEncoder = new OrderRejectedEncoder();
    private final OrderCanceledEncoder canceledEncoder = new OrderCanceledEncoder();
    private final OrderMatchedEncoder matchedEncoder = new OrderMatchedEncoder();

    private long acceptedCount, rejectedCount, matchedCount, canceledCount;

    public void init() {
        this.publication = AeronUtil.aeron().addPublication(AeronChannel.REPORT_FLOW, AeronChannel.REPORT_STREAM_ID);
        log.info("ExecutionReporter 已初始化，Aeron report channel 就緒");
    }

    public void reportAccepted(Order taker) {
        StaticMetricsHolder.addCounter(MetricsKey.ORDER_ACCEPTED_COUNT, 1);
        acceptedCount++;
        int len = HEADER_SIZE + OrderAcceptedEncoder.BLOCK_LENGTH;
        AeronUtil.send(publication, len, (buf, off) -> {
            writeFrameHeader(buf, off, MsgType.ORDER_ACCEPTED);
            acceptedEncoder.wrapAndApplyHeader(buf, off + SBE_HEADER_OFFSET, headerEncoder)
                .timestamp(System.nanoTime())
                .userId(taker.getUserId())
                .orderId(taker.getOrderId())
                .clientOrderId(taker.getClientOrderId());
        });
    }

    public void reportRejected(long userId, long clientOrderId) {
        StaticMetricsHolder.addCounter(MetricsKey.ORDER_REJECTED_COUNT, 1);
        rejectedCount++;
        int len = HEADER_SIZE + OrderRejectedEncoder.BLOCK_LENGTH;
        AeronUtil.send(publication, len, (buf, off) -> {
            writeFrameHeader(buf, off, MsgType.ORDER_REJECTED);
            rejectedEncoder.wrapAndApplyHeader(buf, off + SBE_HEADER_OFFSET, headerEncoder)
                .timestamp(System.nanoTime())
                .userId(userId)
                .clientOrderId(clientOrderId);
        });
    }

    public void reportMatch(Order taker, Order maker, Trade trade) {
        matchedCount++;
        // 分別給 taker 和 maker 發送成交回報
        sendMatchReport(taker, trade);
        sendMatchReport(maker, trade);
    }

    private void sendMatchReport(Order order, Trade trade) {
        int len = HEADER_SIZE + OrderMatchedEncoder.BLOCK_LENGTH;
        AeronUtil.send(publication, len, (buf, off) -> {
            writeFrameHeader(buf, off, MsgType.ORDER_MATCHED);
            matchedEncoder.wrapAndApplyHeader(buf, off + SBE_HEADER_OFFSET, headerEncoder)
                .timestamp(System.nanoTime())
                .userId(order.getUserId())
                .orderId(order.getOrderId())
                .status(OrderStatus.get((short) order.getStatus()))
                .lastPrice(trade.getPrice())
                .lastQty(trade.getQty())
                .cumQty(order.getFilled())
                .avgPrice(trade.getPrice()) // 簡化：單一成交價
                .clientOrderId(order.getClientOrderId());
        });
    }

    public void reportCanceled(Order order) {
        canceledCount++;
        int len = HEADER_SIZE + OrderCanceledEncoder.BLOCK_LENGTH;
        AeronUtil.send(publication, len, (buf, off) -> {
            writeFrameHeader(buf, off, MsgType.ORDER_CANCELED);
            canceledEncoder.wrapAndApplyHeader(buf, off + SBE_HEADER_OFFSET, headerEncoder)
                .timestamp(System.nanoTime())
                .userId(order.getUserId())
                .orderId(order.getOrderId())
                .filledQty(order.getFilled())
                .clientOrderId(order.getClientOrderId());
        });
    }

    public void reportAuth(long userId) {}
    public void reportDeposit(long userId, int assetId, long amount) {}

    private void writeFrameHeader(org.agrona.MutableDirectBuffer buf, int off, int msgType) {
        buf.putInt(off, msgType, ByteOrder.LITTLE_ENDIAN);
        buf.putLong(off + 4, 0L, ByteOrder.LITTLE_ENDIAN); // reserved
    }

    @Override
    public void close() {
        log.info("ExecutionReporter closing: accepted={}, rejected={}, matched={}, canceled={}",
                acceptedCount, rejectedCount, matchedCount, canceledCount);
        if (publication != null) publication.close();
    }
}
