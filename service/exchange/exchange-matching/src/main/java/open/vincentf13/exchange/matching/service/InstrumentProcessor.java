package open.vincentf13.exchange.matching.service;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.Getter;
import open.vincentf13.exchange.common.sdk.ExchangeMetric;
import open.vincentf13.exchange.matching.domain.match.result.MatchResult;
import open.vincentf13.exchange.matching.domain.order.book.Order;
import open.vincentf13.exchange.matching.domain.order.book.OrderBook;
import open.vincentf13.exchange.matching.infra.MatchingEvent;
import open.vincentf13.exchange.matching.infra.snapshot.InstrumentSnapshot;
import open.vincentf13.exchange.matching.infra.snapshot.SnapshotState;
import open.vincentf13.exchange.matching.infra.wal.InstrumentWal;
import open.vincentf13.exchange.matching.infra.wal.WalEntry;
import open.vincentf13.sdk.core.log.OpenLog;
import open.vincentf13.sdk.core.metrics.MCounter;
import open.vincentf13.sdk.core.metrics.MGauge;
import open.vincentf13.sdk.core.metrics.MTimer;
import open.vincentf13.sdk.core.metrics.enums.SysMetric;
import open.vincentf13.sdk.core.validator.OpenValidator;

public class InstrumentProcessor {

  @Getter private final Long instrumentId;
  @Getter private final InstrumentWal wal;
  private final InstrumentSnapshot snapshotStore;
  private final OrderBook orderBook;
  @Getter private final ExecutorService executor;

  private SnapshotState snapshotState;

  public InstrumentProcessor(Long instrumentId) {
    this.instrumentId = instrumentId;
    this.wal = new InstrumentWal(instrumentId);
    this.snapshotStore = new InstrumentSnapshot(instrumentId);
    this.orderBook = new OrderBook();
    // 使用 newFixedThreadPool 代替 newSingleThreadExecutor 以便 Micrometer 能取得 ThreadPoolExecutor 的內部指標
    // (active, queued, etc.)
    ExecutorService rawExecutor =
        Executors.newFixedThreadPool(1, r -> new Thread(r, "matching-" + instrumentId));

    // 埋點：監控撮合引擎執行緒池，並取得包裝後的實例以啟用計時功能
    this.executor =
        MGauge.monitorExecutor(SysMetric.EXECUTOR, rawExecutor, "matching-" + instrumentId, null);
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

    // 埋點：監控撮合批次處理延遲
    MTimer.record(
        ExchangeMetric.MATCHING_LATENCY,
        () -> {
          long lastSeq = -1L;
          for (Order order : batch) {
            if (!Objects.equals(order.getInstrumentId(), instrumentId)) {
              OpenLog.warn(
                  MatchingEvent.ORDER_ROUTING_ERROR,
                  "expected",
                  instrumentId,
                  "actual",
                  order.getInstrumentId());
              continue;
            }
            Objects.requireNonNull(order, "order batch item must not be null");
            OpenValidator.validateOrThrow(order);

            if (orderBook.alreadyProcessed(order.getOrderId())) {
              continue;
            }

            // 1. Calculate Match
            MatchResult result = orderBook.match(order);

            // 2. Persist to WAL (Sequentially)
            List<WalEntry> appended = wal.appendBatch(List.of(result));
            if (appended.isEmpty()) {
              continue;
            }

            // 3. Apply to OrderBook (Update State)
            orderBook.apply(result);
            orderBook.markProcessed(result.getTakerOrder().getOrderId());
            lastSeq = appended.get(0).getSeq();

            // 埋點：如果成交，增加成交次數
            if (result.getTrades() != null && !result.getTrades().isEmpty()) {
              MCounter.inc(
                  ExchangeMetric.MATCHING_TRADE,
                  result.getTrades().size(),
                  "symbol",
                  String.valueOf(instrumentId));
            }
          }

          // 4. Snapshot Check (Once per batch)
          if (lastSeq > 0) {
            snapshotStore.maybeSnapshot(lastSeq, orderBook);
          }
        },
        "symbol",
        String.valueOf(instrumentId));
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
        OpenLog.error(
            MatchingEvent.WAL_REPLAY_FAILED,
            ex,
            "seq",
            entry.getSeq(),
            "instrumentId",
            instrumentId);
      }
    }
  }

  public void shutdown() {
    executor.shutdown();
  }
}
