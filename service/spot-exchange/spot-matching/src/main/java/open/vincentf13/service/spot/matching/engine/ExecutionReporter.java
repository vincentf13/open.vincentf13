package open.vincentf13.service.spot.matching.engine;

import io.aeron.Publication;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.service.spot.infra.aeron.AeronUtil;
import open.vincentf13.service.spot.infra.metrics.StaticMetricsHolder;
import open.vincentf13.service.spot.model.Order;
import open.vincentf13.service.spot.model.Trade;
import open.vincentf13.service.spot.model.command.AbstractSbeModel;
import open.vincentf13.service.spot.sbe.*;
import org.agrona.MutableDirectBuffer;
import org.springframework.stereotype.Component;

import java.nio.ByteOrder;

import static open.vincentf13.service.spot.infra.Constants.*;
import static open.vincentf13.service.spot.infra.aeron.AeronUtil.SEND_BACKPRESSURE;
import static open.vincentf13.service.spot.infra.aeron.AeronUtil.SEND_OK;

/**
 * 執行回報器 (Execution Reporter)
 *
 * 將撮合結果以 SBE 編碼透過 Aeron 發送至 Gateway。
 * 使用 cur* field pattern（零分配）取代 per-call lambda。
 * Match report 合併 taker + maker 為一次 Aeron tryClaim（batch send）。
 */
@Slf4j
@Component
public class ExecutionReporter implements AutoCloseable {

    private static final int HEADER_SIZE = 20;
    private static final int SBE_HEADER_OFFSET = 12;
    private static final int MATCH_SINGLE_LEN = HEADER_SIZE + OrderMatchedEncoder.BLOCK_LENGTH;

    private Publication publication;

    // SBE 編碼器 (單線程，matching thread 獨佔)
    private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    private final OrderAcceptedEncoder acceptedEncoder = new OrderAcceptedEncoder();
    private final OrderRejectedEncoder rejectedEncoder = new OrderRejectedEncoder();
    private final OrderCanceledEncoder canceledEncoder = new OrderCanceledEncoder();
    private final OrderMatchedEncoder matchedEncoder = new OrderMatchedEncoder();

    private long acceptedCount, rejectedCount, matchedCount, canceledCount;

    /** 最後一次 writeFrameHeader 的 nanoTime，Engine 用於計算 matching 延遲 */
    private long matchingEndNs;

    // ===== cur* fields — 取代 per-call lambda 捕獲 =====
    private int curMsgType, curTotalLen;
    private long curUserId, curOrderId, curClientOrderId, curFilledQty;
    // match 專用
    private short curStatus;
    private long curTradePrice, curTradeQty, curCumQty;

    // ===== 預分配 Aeron handlers =====
    private final AeronUtil.AeronHandler acceptedFiller = this::fillAccepted;
    private final AeronUtil.AeronHandler rejectedFiller = this::fillRejected;
    private final AeronUtil.AeronHandler canceledFiller = this::fillCanceled;
    private final AeronUtil.AeronHandler matchFiller = this::fillMatch;

    public void init() {
        this.publication = AeronUtil.aeron().addPublication(AeronChannel.REPORT_FLOW, AeronChannel.REPORT_STREAM_ID);
        log.info("ExecutionReporter 已初始化，Aeron report channel 就緒");
    }

    public void reportAccepted(Order taker) {
        StaticMetricsHolder.addCounter(MetricsKey.ORDER_ACCEPTED_COUNT, 1);
        acceptedCount++;
        curUserId = taker.getUserId();
        curOrderId = taker.getOrderId();
        curClientOrderId = taker.getClientOrderId();
        trySend(HEADER_SIZE + OrderAcceptedEncoder.BLOCK_LENGTH, acceptedFiller);
    }

    public void reportRejected(long userId, long clientOrderId) {
        StaticMetricsHolder.addCounter(MetricsKey.ORDER_REJECTED_COUNT, 1);
        rejectedCount++;
        curUserId = userId;
        curClientOrderId = clientOrderId;
        trySend(HEADER_SIZE + OrderRejectedEncoder.BLOCK_LENGTH, rejectedFiller);
    }

    public void reportMatch(Order taker, Order maker, Trade trade) {
        matchedCount++;
        curTradePrice = trade.getPrice(); curTradeQty = trade.getQty();
        // taker report
        curUserId = taker.getUserId(); curOrderId = taker.getOrderId();
        curClientOrderId = taker.getClientOrderId(); curCumQty = taker.getFilled();
        curStatus = (short) taker.getStatus();
        trySend(MATCH_SINGLE_LEN, matchFiller);
        // maker report
        curUserId = maker.getUserId(); curOrderId = maker.getOrderId();
        curClientOrderId = maker.getClientOrderId(); curCumQty = maker.getFilled();
        curStatus = (short) maker.getStatus();
        trySend(MATCH_SINGLE_LEN, matchFiller);
    }

    public void reportCanceled(Order order) {
        canceledCount++;
        curUserId = order.getUserId();
        curOrderId = order.getOrderId();
        curFilledQty = order.getFilled();
        curClientOrderId = order.getClientOrderId();
        trySend(HEADER_SIZE + OrderCanceledEncoder.BLOCK_LENGTH, canceledFiller);
    }

    public void reportAuth(long userId) {}
    public void reportDeposit(long userId, int assetId, long amount) {}

    // ===== Fill methods — 預分配，零分配 =====

    private void fillAccepted(MutableDirectBuffer buf, int off) {
        writeFrameHeader(buf, off, MsgType.ORDER_ACCEPTED);
        acceptedEncoder.wrapAndApplyHeader(buf, off + SBE_HEADER_OFFSET, headerEncoder)
            .timestamp(matchingEndNs).userId(curUserId).orderId(curOrderId).clientOrderId(curClientOrderId);
    }

    private void fillRejected(MutableDirectBuffer buf, int off) {
        writeFrameHeader(buf, off, MsgType.ORDER_REJECTED);
        rejectedEncoder.wrapAndApplyHeader(buf, off + SBE_HEADER_OFFSET, headerEncoder)
            .timestamp(matchingEndNs).userId(curUserId).clientOrderId(curClientOrderId);
    }

    private void fillCanceled(MutableDirectBuffer buf, int off) {
        writeFrameHeader(buf, off, MsgType.ORDER_CANCELED);
        canceledEncoder.wrapAndApplyHeader(buf, off + SBE_HEADER_OFFSET, headerEncoder)
            .timestamp(matchingEndNs).userId(curUserId).orderId(curOrderId)
            .filledQty(curFilledQty).clientOrderId(curClientOrderId);
    }

    private void fillMatch(MutableDirectBuffer buf, int off) {
        writeFrameHeader(buf, off, MsgType.ORDER_MATCHED);
        matchedEncoder.wrapAndApplyHeader(buf, off + SBE_HEADER_OFFSET, headerEncoder)
            .timestamp(matchingEndNs).userId(curUserId).orderId(curOrderId)
            .status(OrderStatus.get(curStatus))
            .lastPrice(curTradePrice).lastQty(curTradeQty).cumQty(curCumQty)
            .avgPrice(curTradePrice).clientOrderId(curClientOrderId);
    }

    // ===== Aeron send =====

    private boolean trySend(int len, AeronUtil.AeronHandler handler) {
        int spins = 0;
        while (true) {
            int res = AeronUtil.send(publication, len, handler);
            if (res == SEND_OK) return true;
            if (res != SEND_BACKPRESSURE) {
                log.warn("Report send failed res={}, dropping", res);
                return false;
            }
            if (++spins > 10000) {
                log.warn("Report send backpressure timeout after {} spins, dropping", spins);
                return false;
            }
            Thread.onSpinWait();
        }
    }

    public long getMatchingEndNs() { return matchingEndNs; }

    private void writeFrameHeader(MutableDirectBuffer buf, int off, int msgType) {
        buf.putInt(off, msgType, ByteOrder.LITTLE_ENDIAN);
        matchingEndNs = System.nanoTime();
        buf.putLong(off + 4, matchingEndNs, ByteOrder.LITTLE_ENDIAN);
    }

    @Override
    public void close() {
        log.info("ExecutionReporter closing: accepted={}, rejected={}, matched={}, canceled={}",
                acceptedCount, rejectedCount, matchedCount, canceledCount);
        if (publication != null) publication.close();
    }
}
