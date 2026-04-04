package open.vincentf13.service.spot.matching.engine;

import open.vincentf13.service.spot.model.Order;
import open.vincentf13.service.spot.model.Trade;

import java.util.ArrayDeque;
import java.util.Deque;

import static open.vincentf13.service.spot.infra.Constants.MatchingConfig;

/**
 * 撮合引擎物件池 (Matching Object Pool)
 *
 * 職責：集中管理 Order、Trade、Deque 的預分配與回收，
 * 確保撮合熱路徑 Zero-GC。全域共享，單線程存取（matching thread）。
 */
public class MatchingPool {
    private static final Deque<Order> ORDERS = new ArrayDeque<>(MatchingConfig.INITIAL_BOOK_ORDER_COUNT);
    private static final Deque<Trade> TRADES = new ArrayDeque<>(20_000);
    private static final Deque<Deque<Order>> DEQUES = new ArrayDeque<>(10_000);

    static {
        for (int i = 0; i < MatchingConfig.INITIAL_BOOK_ORDER_COUNT; i++) {
            ORDERS.add(new Order());
            if (i < 20_000) TRADES.add(new Trade());
            if (i < 10_000) DEQUES.add(new ArrayDeque<>(MatchingConfig.INITIAL_BOOK_LEVEL_CAPACITY));
        }
    }

    public static Order borrowOrder() {
        Order o = ORDERS.pollFirst();
        return o != null ? o : new Order();
    }

    public static void releaseOrder(Order o) {
        if (o != null) ORDERS.addLast(o);
    }

    public static Trade borrowTrade() {
        Trade t = TRADES.pollFirst();
        return t != null ? t : new Trade();
    }

    public static void releaseTrade(Trade t) {
        if (t != null) TRADES.addLast(t);
    }

    public static Deque<Order> borrowDeque() {
        Deque<Order> d = DEQUES.pollFirst();
        if (d != null) { d.clear(); return d; }
        return new ArrayDeque<>(MatchingConfig.INITIAL_BOOK_LEVEL_CAPACITY);
    }

    public static void releaseDeque(Deque<Order> d) {
        if (d != null) { d.clear(); DEQUES.addLast(d); }
    }

    private MatchingPool() {}
}
