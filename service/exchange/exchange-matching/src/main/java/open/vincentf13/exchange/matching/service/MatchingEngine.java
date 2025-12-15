package open.vincentf13.exchange.matching.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.matching.domain.match.result.MatchResult;
import open.vincentf13.exchange.matching.domain.order.book.OrderBook;
import open.vincentf13.exchange.matching.domain.order.book.Order;
import open.vincentf13.exchange.matching.infra.wal.SnapshotService;
import open.vincentf13.exchange.matching.infra.wal.SnapshotState;
import open.vincentf13.exchange.matching.infra.wal.WalEntry;
import open.vincentf13.exchange.matching.infra.wal.WalService;
import open.vincentf13.sdk.core.OpenValidator;
import open.vincentf13.sdk.core.log.OpenLog;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class MatchingEngine {
    
    private final OrderBook orderBook = new OrderBook();
    private final WalService walService;
    private final SnapshotService snapshotService;
    
    private SnapshotState snapshotState;
    
    @PostConstruct
    public void init() {
        snapshotState = loadSnapshot();
        walService.loadExisting();
        replayWal();
    }
    
    public WalEntry process(Order order) {
        OpenValidator.validateOrThrow(order);
        MatchResult result = orderBook.match(order);
        WalEntry entry = walService.append(result,
                                           orderBook.depthSnapshot(order.getInstrumentId(), 20));
        orderBook.apply(result);
        snapshotService.maybeSnapshot(entry.getSeq(), orderBook);
        return entry;
    }
    
    public List<WalEntry> walEntriesFrom(long seq) {
        return walService.readFrom(seq);
    }
    
    public long latestSeq() {
        return walService.latestSeq();
    }
    
    private SnapshotState loadSnapshot() {
        SnapshotState snapshot = snapshotService.load();
        if (snapshot != null && snapshot.getOpenOrders() != null) {
            snapshot.getOpenOrders().forEach(orderBook::restore);
        }
        return snapshot;
    }
    
    private void replayWal() {
        long startSeq = snapshotState != null ? snapshotState.getLastSeq() + 1 : 1L;
        List<WalEntry> entries = walService.readFrom(startSeq);
        for (WalEntry entry : entries) {
            try {
                orderBook.apply(entry.getMatchResult());
            } catch (Exception ex) {
                OpenLog.error(OpenLog.event("WAL_REPLAY_FAILED"),
                              ex,
                              "seq",
                              entry.getSeq());
            }
        }
    }
}
