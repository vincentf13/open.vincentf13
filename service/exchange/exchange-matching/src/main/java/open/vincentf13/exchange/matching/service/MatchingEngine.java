package open.vincentf13.exchange.matching.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.matching.domain.match.result.MatchResult;
import open.vincentf13.exchange.matching.domain.order.book.OrderBook;
import open.vincentf13.exchange.matching.domain.order.book.Order;
import open.vincentf13.exchange.matching.infra.snapshot.SnapshotService;
import open.vincentf13.exchange.matching.infra.snapshot.SnapshotState;
import open.vincentf13.exchange.matching.infra.wal.WalEntry;
import open.vincentf13.exchange.matching.infra.wal.WalService;
import open.vincentf13.exchange.matching.infra.MatchingEvent;
import open.vincentf13.sdk.core.OpenValidator;
import open.vincentf13.sdk.core.log.OpenLog;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class MatchingEngine {
    
    private final OrderBook orderBook = new OrderBook();
    private final WalService walService;
    private final SnapshotService snapshotService;
    private final Map<Integer, Long> partitionOffsets = new HashMap<>();
    
    private SnapshotState snapshotState;
    
    @PostConstruct
    public void init() {
        snapshotState = loadSnapshot();
        walService.loadExisting();
        replayWal();
    }
    
    public WalEntry process(Order order,
                            int partition,
                            long kafkaOffset) {
        OpenValidator.validateOrThrow(order);
        MatchResult result = orderBook.match(order);
        WalEntry entry = walService.append(result,
                                           orderBook.depthSnapshot(order.getInstrumentId(), 20),
                                           kafkaOffset,
                                           partition);
        orderBook.apply(result);
        partitionOffsets.put(partition, kafkaOffset);
        snapshotService.maybeSnapshot(entry.getSeq(), orderBook, partitionOffsets);
        return entry;
    }
    
    private SnapshotState loadSnapshot() {
        SnapshotState snapshot = snapshotService.load();
        if (snapshot != null) {
            if (snapshot.getPartitionOffsets() != null && !snapshot.getPartitionOffsets().isEmpty()) {
                partitionOffsets.putAll(snapshot.getPartitionOffsets());
            }
            if (snapshot.getOpenOrders() != null) {
                snapshot.getOpenOrders().forEach(orderBook::restore);
            }
        }
        return snapshot;
    }
    
    private void replayWal() {
        long startSeq = snapshotState != null ? snapshotState.getLastSeq() + 1 : 1L;
        List<WalEntry> entries = walService.readFrom(startSeq);
        for (WalEntry entry : entries) {
            try {
                orderBook.apply(entry.getMatchResult());
                if (entry.getPartition() != null && entry.getKafkaOffset() != null) {
                    partitionOffsets.merge(entry.getPartition(), entry.getKafkaOffset(), Math::max);
                }
            } catch (Exception ex) {
                OpenLog.error(MatchingEvent.WAL_REPLAY_FAILED,
                              ex,
                              "seq",
                              entry.getSeq());
            }
        }
    }
    
    public long offsetForPartition(int partition) {
        return partitionOffsets.getOrDefault(partition, -1L);
    }
}
