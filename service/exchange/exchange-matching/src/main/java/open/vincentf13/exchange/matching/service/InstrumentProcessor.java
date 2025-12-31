package open.vincentf13.exchange.matching.service;

import lombok.Getter;
import open.vincentf13.exchange.matching.domain.match.result.MatchResult;
import open.vincentf13.exchange.matching.domain.order.book.Order;
import open.vincentf13.exchange.matching.domain.order.book.OrderBook;
import open.vincentf13.exchange.matching.infra.MatchingEvent;
import open.vincentf13.exchange.matching.infra.snapshot.InstrumentSnapshot;
import open.vincentf13.exchange.matching.infra.snapshot.SnapshotState;
import open.vincentf13.exchange.matching.infra.wal.InstrumentWal;
import open.vincentf13.exchange.matching.infra.wal.WalEntry;
import open.vincentf13.sdk.core.OpenValidator;
import open.vincentf13.sdk.core.log.OpenLog;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class InstrumentProcessor {

    @Getter
    private final Long instrumentId;
    @Getter
    private final InstrumentWal wal;
    private final InstrumentSnapshot snapshotStore;
    private final OrderBook orderBook;
    @Getter
    private final ExecutorService executor;

    private SnapshotState snapshotState;

    public InstrumentProcessor(Long instrumentId) {
        this.instrumentId = instrumentId;
        this.wal = new InstrumentWal(instrumentId);
        this.snapshotStore = new InstrumentSnapshot(instrumentId);
        this.orderBook = new OrderBook();
        this.executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "matching-" + instrumentId));
    }

    public void init() {
        snapshotState = snapshotStore.load();
        restoreSnapshot();
        wal.loadExisting();
        replayWal();
    }

    public void processBatch(List<Order> batch) {
        if (batch == null || batch.isEmpty()) {
            return;
        }

        List<MatchResult> matchResults = new ArrayList<>(batch.size());

        for (Order order : batch) {
            if (!Objects.equals(order.getInstrumentId(), instrumentId)) {
                OpenLog.warn(MatchingEvent.ORDER_ROUTING_ERROR, "expected", instrumentId, "actual", order.getInstrumentId());
                continue;
            }
            Objects.requireNonNull(order, "order batch item must not be null");
            OpenValidator.validateOrThrow(order);

            if (orderBook.alreadyProcessed(order.getOrderId())) {
                continue;
            }

            MatchResult result = orderBook.match(order);
            matchResults.add(result);
        }

        if (matchResults.isEmpty()) {
            return;
        }

        List<WalEntry> appended = wal.appendBatch(matchResults);
        if (appended.isEmpty()) {
            return;
        }

        for (MatchResult result : matchResults) {
            orderBook.apply(result);
            orderBook.markProcessed(result.getTakerOrder().getOrderId());
        }

        long lastSeq = appended.get(appended.size() - 1).getSeq();
        if (lastSeq > 0) {
            snapshotStore.maybeSnapshot(lastSeq, orderBook);
        }
    }

    private void restoreSnapshot() {
        if (snapshotState != null) {
            if (snapshotState.getOpenOrders() != null) {
                snapshotState.getOpenOrders().forEach(orderBook::restore);
            }
            orderBook.restoreProcessedIds(snapshotState.getProcessedOrderIds());
        }
    }

    private void replayWal() {
        long startSeq = snapshotState != null ? snapshotState.getLastSeq() + 1 : 1L;
        List<WalEntry> entries = wal.readFrom(startSeq);
        for (WalEntry entry : entries) {
            try {
                orderBook.apply(entry.getMatchResult());
                orderBook.markProcessed(entry.getMatchResult().getTakerOrder().getOrderId());
            } catch (Exception ex) {
                OpenLog.error(MatchingEvent.WAL_REPLAY_FAILED,
                              ex,
                              "seq",
                              entry.getSeq(),
                              "instrumentId", instrumentId);
            }
        }
    }

    public void shutdown() {
        executor.shutdown();
    }
}
