package open.vincentf13.service.spot.matching.engine;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.map.ChronicleMap;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.infra.util.DecimalUtil;
import open.vincentf13.service.spot.model.CidKey;
import open.vincentf13.service.spot.model.Order;
import open.vincentf13.service.spot.infra.thread.ThreadContext;
import open.vincentf13.service.spot.infra.Constants.OrderSide;
import open.vincentf13.service.spot.sbe.Side;
import org.springframework.stereotype.Component;

/**
 * 訂單處理器 (Zero-GC 優化版)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderProcessor implements OrderBook.TradeFinalizer {
    public static final class RecoveryState {
        private final long maxOrderId;

        private RecoveryState(long maxOrderId) {
            this.maxOrderId = maxOrderId;
        }

        public long maxOrderId() {
            return maxOrderId;
        }
    }

    private static final class SettlementAmounts {
        private final long quoteFloor;
        private final long quoteCeil;

        private SettlementAmounts(long quoteFloor, long quoteCeil) {
            this.quoteFloor = quoteFloor;
            this.quoteCeil = quoteCeil;
        }
    }

    private final ChronicleMap<open.vincentf13.service.spot.infra.chronicle.LongValue, Order> orders = Storage.self().orders();
    private final ChronicleMap<CidKey, open.vincentf13.service.spot.infra.chronicle.LongValue> clientOrderIdDiskMap = Storage.self().clientOrderIdMap();
    private final ChronicleMap<open.vincentf13.service.spot.infra.chronicle.LongValue, Boolean> activeOrdersDiskMap = Storage.self().activeOrders();

    // --- 性能優化：冪等鍵寫緩衝 (零對象分配 Circular Buffer 版) ---
    private static final int BUFFER_SIZE = 4096;
    private final long[] pendingUids = new long[BUFFER_SIZE];
    private final long[] pendingCidsArr = new long[BUFFER_SIZE];
    private final long[] pendingOids = new long[BUFFER_SIZE];
    private int bufferCount = 0;

    private final Ledger ledger;
    private final ExecutionReporter reporter;

    private final CidKey reusableFlushKey = new CidKey();
    private final open.vincentf13.service.spot.infra.chronicle.LongValue reusableFlushValue = new open.vincentf13.service.spot.infra.chronicle.LongValue();
    private final open.vincentf13.service.spot.infra.chronicle.LongValue reusableOrderKey = new open.vincentf13.service.spot.infra.chronicle.LongValue();

    @PostConstruct
    public void init() { 
        log.info("OrderProcessor 初始化完成。"); 
    }

    /** 核心落地：將緩衝的冪等鍵批量寫入磁碟，實現零分配 */
    public void flush() {
        if (bufferCount > 0) {
            for (int i = 0; i < bufferCount; i++) {
                reusableFlushKey.set(pendingUids[i], pendingCidsArr[i]);
                reusableFlushValue.set(pendingOids[i]);
                clientOrderIdDiskMap.put(reusableFlushKey, reusableFlushValue);
            }
            bufferCount = 0;
        }
    }

    public RecoveryState coldStartRebuild() {
        log.warn("未檢測到有效內存快照，正在執行耗時的全量磁碟掃描以恢復狀態...");
        clientOrderIdDiskMap.clear();
        activeOrdersDiskMap.clear();
        OrderBook.resetForRecovery();

        final long[] maxOrderId = new long[1];
        orders.forEach((orderIdKey, diskOrder) -> {
            if (diskOrder == null) return;

            Order order = new Order();
            order.copyFrom(diskOrder);
            order.validateState();
            maxOrderId[0] = Math.max(maxOrderId[0], order.getOrderId());

            if (order.getClientOrderId() > 0) {
                clientOrderIdDiskMap.put(new CidKey(order.getUserId(), order.getClientOrderId()), new open.vincentf13.service.spot.infra.chronicle.LongValue(order.getOrderId()));
            }
            if (!order.isTerminal()) {
                activeOrdersDiskMap.put(new open.vincentf13.service.spot.infra.chronicle.LongValue(order.getOrderId()), Boolean.TRUE);
                OrderBook.get(order.getSymbolId()).recoverOrder(order);
            }
        });
        return new RecoveryState(maxOrderId[0]);
    }

    /** 核心入口：處理下單指令 */
    public void processCreateCommand(long userId, int symbolId, long price, long qty, Side side, long clientOrderId, long gatewaySequence, long timestamp, open.vincentf13.service.spot.model.WalProgress progress) {
        if (userId <= 0 || clientOrderId <= 0 || qty <= 0 || (side == Side.BUY && price <= 0)) {
            reporter.reportRejected(userId, clientOrderId);
            return;
        }

        final ThreadContext context = ThreadContext.get();
        final CidKey cidKey = context.getCidKey();
        cidKey.set(userId, clientOrderId);

        if (isBufferedDuplicate(userId, clientOrderId)) return;
        if (clientOrderIdDiskMap.containsKey(cidKey)) return;

        handleOrderCreate(userId, symbolId, price, qty, side, clientOrderId, gatewaySequence, timestamp, progress);
    }

    /** 處理撤單指令 */
    public void processCancelCommand(long userId, long orderId, long gatewaySequence) {
        if (userId <= 0 || orderId <= 0) return;

        reusableOrderKey.set(orderId);
        Order order = orders.get(reusableOrderKey);
        if (order == null || order.getUserId() != userId || !order.isActive()) return;
        order.validateState();

        OrderBook book = OrderBook.get(order.getSymbolId());
        Order canceled = book.cancel(orderId, userId, gatewaySequence);
        if (canceled == null) return;

        long frozenAmount = canceled.getFrozen();
        int assetId = (canceled.getSide() == OrderSide.BUY) ? book.getQuoteAssetId() : book.getBaseAssetId();
        ledger.unfreezeBalance(canceled.getUserId(), assetId, frozenAmount, gatewaySequence);
        canceled.setFrozen(0);
        canceled.validateState();
        reporter.reportCanceled(canceled);
    }

    /** 實作 TradeFinalizer 接口以複用 */
    @Override
    public void onMatch(open.vincentf13.service.spot.model.Trade trade, Order maker, Order taker, int baseAsset, int quoteAsset) {
        long tradePrice = trade.getPrice();
        long tradeQty = trade.getQty();
        validateMatchInputs(trade, maker, taker);
        long mFrozenDelta, tFrozenDelta;
        
        if (maker.getSide() == OrderSide.BUY) {
            // Maker 是買方：計算此次成交應釋放的凍結金額
            long remainingQty = maker.remainingQty();
            long nextFrozen = DecimalUtil.mulCeil(maker.getPrice(), remainingQty);
            mFrozenDelta = maker.getFrozen() - nextFrozen;
            maker.setFrozen(nextFrozen);
        } else {
            // Maker 是賣方：凍結的是 Base， delta 就是 tradeQty
            mFrozenDelta = tradeQty;
            maker.setFrozen(maker.getFrozen() - tradeQty);
        }

        if (taker.getSide() == OrderSide.BUY) {
            // Taker 是買方
            long remainingQty = taker.remainingQty();
            long nextFrozen = DecimalUtil.mulCeil(taker.getPrice(), remainingQty);
            tFrozenDelta = taker.getFrozen() - nextFrozen;
            taker.setFrozen(nextFrozen);
        } else {
            // Taker 是賣方
            tFrozenDelta = tradeQty;
            taker.setFrozen(taker.getFrozen() - tradeQty);
        }

        if (mFrozenDelta < 0 || tFrozenDelta < 0) {
            throw new IllegalStateException(
                "Negative frozen delta, makerOrderId=%d, takerOrderId=%d".formatted(maker.getOrderId(), taker.getOrderId())
            );
        }

        SettlementAmounts settlement = validateSettlementAmounts(trade, maker, taker, mFrozenDelta, tFrozenDelta);
        ledger.settleTrade(maker.getUserId(), taker.getUserId(), tradePrice, tradeQty, taker.getSide(), mFrozenDelta, tFrozenDelta, trade.getLastSeq(), baseAsset, quoteAsset, trade.getTradeId());
        validateTradeConservation(trade, taker.getSide(), mFrozenDelta, tFrozenDelta, settlement);
        maker.validateState();
        taker.validateState();
        reporter.reportMatch(taker, maker, trade);
    }

    private void handleOrderCreate(long userId, int symbolId, long price, long quantity, Side side, long clientOrderId, long gatewaySequence, long timestamp,
                                   open.vincentf13.service.spot.model.WalProgress progress) {
        OrderBook book;
        try {
            book = OrderBook.get(symbolId);
        } catch (IllegalArgumentException ex) {
            reporter.reportRejected(userId, clientOrderId);
            return;
        }

        int assetId = (side == Side.BUY) ? book.getQuoteAssetId() : book.getBaseAssetId(); 
        long freezeAmount = (side == Side.BUY) ? DecimalUtil.mulCeil(price, quantity) : quantity;

        if (!ledger.freezeBalance(userId, assetId, freezeAmount, gatewaySequence)) {
            reporter.reportRejected(userId, clientOrderId);
            return;
        }

        long orderId = progress.nextOrderId();
        Order taker = book.handleCreate(orderId, userId, symbolId, price, quantity, side, clientOrderId, timestamp, gatewaySequence, freezeAmount, progress, this);
        taker.validateState();
        appendCidMapping(userId, clientOrderId, orderId);

        if (taker != null) reporter.reportAccepted(taker);
    }

    private void validateMatchInputs(open.vincentf13.service.spot.model.Trade trade, Order maker, Order taker) {
        if (trade.getQty() <= 0 || trade.getPrice() <= 0) {
            throw new IllegalStateException("Invalid trade payload, tradeId=" + trade.getTradeId());
        }
        if (maker.getSide() == taker.getSide()) {
            throw new IllegalStateException(
                "Maker and taker have same side, makerOrderId=%d, takerOrderId=%d".formatted(maker.getOrderId(), taker.getOrderId())
            );
        }
        if (!maker.isActive() || !taker.isActive()) {
            throw new IllegalStateException(
                "Matched terminal order, makerOrderId=%d, takerOrderId=%d".formatted(maker.getOrderId(), taker.getOrderId())
            );
        }
        if (trade.getQty() > maker.remainingQty() || trade.getQty() > taker.remainingQty()) {
            throw new IllegalStateException(
                "Trade quantity exceeds remaining quantity, makerOrderId=%d, takerOrderId=%d".formatted(maker.getOrderId(), taker.getOrderId())
            );
        }
    }

    private SettlementAmounts validateSettlementAmounts(open.vincentf13.service.spot.model.Trade trade, Order maker, Order taker, long mFrozenDelta, long tFrozenDelta) {
        long quoteFloor = DecimalUtil.mulFloor(trade.getPrice(), trade.getQty());
        long quoteCeil = DecimalUtil.mulCeil(trade.getPrice(), trade.getQty());

        if (maker.getSide() == OrderSide.BUY) {
            if (mFrozenDelta < quoteCeil) {
                throw new IllegalStateException(
                    "Buyer frozen delta below required quote ceil, orderId=%d, required=%d, actual=%d"
                        .formatted(maker.getOrderId(), quoteCeil, mFrozenDelta)
                );
            }
            if (tFrozenDelta != trade.getQty()) {
                throw new IllegalStateException(
                    "Seller frozen delta must equal trade quantity, orderId=%d, expected=%d, actual=%d"
                        .formatted(taker.getOrderId(), trade.getQty(), tFrozenDelta)
                );
            }
        } else {
            if (tFrozenDelta < quoteCeil) {
                throw new IllegalStateException(
                    "Buyer frozen delta below required quote ceil, orderId=%d, required=%d, actual=%d"
                        .formatted(taker.getOrderId(), quoteCeil, tFrozenDelta)
                );
            }
            if (mFrozenDelta != trade.getQty()) {
                throw new IllegalStateException(
                    "Seller frozen delta must equal trade quantity, orderId=%d, expected=%d, actual=%d"
                        .formatted(maker.getOrderId(), trade.getQty(), mFrozenDelta)
                );
            }
        }
        return new SettlementAmounts(quoteFloor, quoteCeil);
    }

    private void validateTradeConservation(open.vincentf13.service.spot.model.Trade trade, byte takerSide, long mFrozenDelta, long tFrozenDelta, SettlementAmounts settlement) {
        long baseNet;
        long quoteNet;
        if (takerSide == OrderSide.BUY) {
            baseNet = trade.getQty() - trade.getQty();
            quoteNet = (tFrozenDelta - settlement.quoteCeil) + settlement.quoteFloor + (settlement.quoteCeil - settlement.quoteFloor) - tFrozenDelta;
        } else {
            baseNet = trade.getQty() - tFrozenDelta + mFrozenDelta - trade.getQty();
            quoteNet = settlement.quoteFloor + (mFrozenDelta - settlement.quoteCeil) + tradeFeeDelta(settlement) - mFrozenDelta;
        }
        if (baseNet != 0 || quoteNet != 0) {
            throw new IllegalStateException(
                "Trade conservation violated, tradeId=%d, baseNet=%d, quoteNet=%d"
                    .formatted(trade.getTradeId(), baseNet, quoteNet)
            );
        }
    }

    private long tradeFeeDelta(SettlementAmounts settlement) {
        return settlement.quoteCeil - settlement.quoteFloor;
    }

    private boolean isBufferedDuplicate(long userId, long clientOrderId) {
        for (int i = 0; i < bufferCount; i++) {
            if (pendingUids[i] == userId && pendingCidsArr[i] == clientOrderId) return true;
        }
        return false;
    }

    private void appendCidMapping(long userId, long clientOrderId, long orderId) {
        if (bufferCount >= BUFFER_SIZE) flush();
        pendingUids[bufferCount] = userId;
        pendingCidsArr[bufferCount] = clientOrderId;
        pendingOids[bufferCount] = orderId;
        bufferCount++;
    }
}
