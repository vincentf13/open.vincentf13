package open.vincentf13.service.spot.matching.engine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.map.ChronicleMap;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.infra.util.DecimalUtil;
import open.vincentf13.service.spot.model.CidKey;
import open.vincentf13.service.spot.model.Order;
import open.vincentf13.service.spot.model.Trade;
import open.vincentf13.service.spot.model.WalProgress;
import open.vincentf13.service.spot.infra.Constants.OrderSide;
import open.vincentf13.service.spot.sbe.Side;
import org.springframework.stereotype.Component;

/**
 * 訂單處理器 (Order Processor)
 *
 * 職責：下單/撤單指令的業務流程編排。
 * 冪等去重委派給 {@link IdempotencyGuard}，撮合委派給 {@link OrderBook}，
 * 資產凍結委派給 {@link Ledger}，回報委派給 {@link ExecutionReporter}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderProcessor implements OrderBook.TradeFinalizer {

    private final ChronicleMap<open.vincentf13.service.spot.infra.chronicle.LongValue, Order> orders = Storage.self().orders();
    private final ChronicleMap<open.vincentf13.service.spot.infra.chronicle.LongValue, Boolean> activeOrdersDiskMap = Storage.self().activeOrders();

    private final Ledger ledger;
    private final ExecutionReporter reporter;
    private final IdempotencyGuard idempotencyGuard = new IdempotencyGuard();

    private final open.vincentf13.service.spot.infra.chronicle.LongValue reusableOrderKey = new open.vincentf13.service.spot.infra.chronicle.LongValue();
    private final Order reusableDiskOrder = new Order();

    // ========== 公開 API ==========

    public void flush() { idempotencyGuard.flush(); }

    /** 冷啟動：全量磁碟掃描恢復 OrderBook + IdempotencyGuard */
    public long coldStartRebuild() {
        log.warn("未檢測到有效內存快照，正在執行全量磁碟掃描恢復...");
        idempotencyGuard.clearDisk();
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
                idempotencyGuard.record(order.getUserId(), order.getClientOrderId(), order.getOrderId());
            }
            if (!order.isTerminal()) {
                activeOrdersDiskMap.put(new open.vincentf13.service.spot.infra.chronicle.LongValue(order.getOrderId()), Boolean.TRUE);
                OrderBook.get(order.getSymbolId()).recoverOrder(order);
            }
        });
        idempotencyGuard.flush();
        return maxOrderId[0];
    }

    // ========== 指令處理 ==========

    public void processCreateCommand(long userId, int symbolId, long price, long qty, Side side,
                                     long clientOrderId, long gatewaySequence, long timestamp, WalProgress progress) {
        if (userId <= 0 || clientOrderId <= 0 || qty <= 0 || (side == Side.BUY && price <= 0)) {
            reporter.reportRejected(userId, clientOrderId);
            return;
        }

        if (idempotencyGuard.isDuplicate(userId, clientOrderId)) return;

        handleOrderCreate(userId, symbolId, price, qty, side, clientOrderId, gatewaySequence, timestamp, progress);
    }

    public void processCancelCommand(long userId, long orderId, long gatewaySequence) {
        if (userId <= 0 || orderId <= 0) return;

        reusableOrderKey.set(orderId);
        Order order = orders.getUsing(reusableOrderKey, reusableDiskOrder);
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

    // ========== TradeFinalizer ==========

    @Override
    public void onMatch(Trade trade, Order maker, Order taker, int baseAsset, int quoteAsset) {
        long tradeQty = trade.getQty();
        validateMatchInputs(trade, maker, taker);
        long mFrozenDelta, tFrozenDelta;

        if (maker.getSide() == OrderSide.BUY) {
            long nextFrozen = DecimalUtil.mulCeil(maker.getPrice(), maker.remainingQty());
            mFrozenDelta = maker.getFrozen() - nextFrozen;
            maker.setFrozen(nextFrozen);
        } else {
            mFrozenDelta = tradeQty;
            maker.setFrozen(maker.getFrozen() - tradeQty);
        }

        if (taker.getSide() == OrderSide.BUY) {
            long nextFrozen = DecimalUtil.mulCeil(taker.getPrice(), taker.remainingQty());
            tFrozenDelta = taker.getFrozen() - nextFrozen;
            taker.setFrozen(nextFrozen);
        } else {
            tFrozenDelta = tradeQty;
            taker.setFrozen(taker.getFrozen() - tradeQty);
        }

        if (mFrozenDelta < 0 || tFrozenDelta < 0) {
            throw new IllegalStateException("Negative frozen delta, maker=%d, taker=%d".formatted(maker.getOrderId(), taker.getOrderId()));
        }

        validateSettlementAmounts(trade, maker, taker, mFrozenDelta, tFrozenDelta);
        ledger.settleTrade(maker.getUserId(), taker.getUserId(), trade.getPrice(), tradeQty, taker.getSide(),
                mFrozenDelta, tFrozenDelta, trade.getLastSeq(), baseAsset, quoteAsset, trade.getTradeId());
        maker.validateState();
        taker.validateState();
        reporter.reportMatch(taker, maker, trade);
    }

    // ========== 內部方法 ==========

    private void handleOrderCreate(long userId, int symbolId, long price, long quantity, Side side,
                                   long clientOrderId, long gatewaySequence, long timestamp, WalProgress progress) {
        OrderBook book;
        try { book = OrderBook.get(symbolId); }
        catch (IllegalArgumentException ex) { reporter.reportRejected(userId, clientOrderId); return; }

        int assetId = (side == Side.BUY) ? book.getQuoteAssetId() : book.getBaseAssetId();
        long freezeAmount = (side == Side.BUY) ? DecimalUtil.mulCeil(price, quantity) : quantity;

        if (!ledger.freezeBalance(userId, assetId, freezeAmount, gatewaySequence)) {
            reporter.reportRejected(userId, clientOrderId);
            return;
        }

        long orderId = progress.nextOrderId();
        Order taker = book.handleCreate(orderId, userId, symbolId, price, quantity, side, clientOrderId,
                timestamp, gatewaySequence, freezeAmount, progress, this);
        taker.validateState();
        idempotencyGuard.record(userId, clientOrderId, orderId);
        reporter.reportAccepted(taker);
    }

    private void validateMatchInputs(Trade trade, Order maker, Order taker) {
        if (trade.getQty() <= 0 || trade.getPrice() <= 0) throw new IllegalStateException("Invalid trade, tradeId=" + trade.getTradeId());
        if (maker.getSide() == taker.getSide()) throw new IllegalStateException("Same side match, maker=%d, taker=%d".formatted(maker.getOrderId(), taker.getOrderId()));
        if (!maker.isActive() || !taker.isActive()) throw new IllegalStateException("Terminal match, maker=%d, taker=%d".formatted(maker.getOrderId(), taker.getOrderId()));
        if (trade.getQty() > maker.remainingQty() || trade.getQty() > taker.remainingQty()) throw new IllegalStateException("Qty exceeds remaining, maker=%d, taker=%d".formatted(maker.getOrderId(), taker.getOrderId()));
    }

    private void validateSettlementAmounts(Trade trade, Order maker, Order taker, long mFrozenDelta, long tFrozenDelta) {
        long quoteCeil = DecimalUtil.mulCeil(trade.getPrice(), trade.getQty());
        if (maker.getSide() == OrderSide.BUY) {
            if (mFrozenDelta < quoteCeil) throw new IllegalStateException("Buyer frozen < quote ceil, orderId=%d".formatted(maker.getOrderId()));
            if (tFrozenDelta != trade.getQty()) throw new IllegalStateException("Seller frozen != qty, orderId=%d".formatted(taker.getOrderId()));
        } else {
            if (tFrozenDelta < quoteCeil) throw new IllegalStateException("Buyer frozen < quote ceil, orderId=%d".formatted(taker.getOrderId()));
            if (mFrozenDelta != trade.getQty()) throw new IllegalStateException("Seller frozen != qty, orderId=%d".formatted(maker.getOrderId()));
        }
    }
}
