package open.vincentf13.exchange.matching.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.matching.domain.match.result.MatchResult;
import open.vincentf13.exchange.matching.domain.order.book.Order;
import open.vincentf13.exchange.matching.domain.order.book.OrderBook;
import open.vincentf13.exchange.matching.infra.MatchingEvent;
import open.vincentf13.exchange.matching.infra.snapshot.SnapshotService;
import open.vincentf13.exchange.matching.infra.snapshot.SnapshotState;
import open.vincentf13.exchange.matching.infra.wal.WalEntry;
import open.vincentf13.exchange.matching.infra.wal.WalService;
import open.vincentf13.exchange.matching.infra.wal.WalService.WalAppendRequest;
import open.vincentf13.sdk.core.OpenValidator;
import open.vincentf13.sdk.core.log.OpenLog;
import org.springframework.stereotype.Component;

import java.util.*;

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
    
    public void processBatch(int partition,
                             List<OrderWithOffset> batch) {
        if (batch == null || batch.isEmpty()) {
            return;
        }
        List<WalAppendRequest> walRequests = new ArrayList<>(batch.size());
        List<MatchResult> matchResults = new ArrayList<>(batch.size());
        for (OrderWithOffset item : batch) {
            Objects.requireNonNull(item, "order batch item must not be null");
            Order order = item.order();
            OpenValidator.validateOrThrow(order);
            partitionOffsets.put(partition, item.offset());
            
            // 冪等
            if (orderBook.alreadyProcessed(order.getOrderId())) {
                continue;
            }
            
            //撮合
            MatchResult result = orderBook.match(order);
            matchResults.add(result);
            walRequests.add(new WalAppendRequest(result,
                                                 orderBook.depthSnapshot(order.getInstrumentId(), 20),
                                                 item.offset(),
                                                 partition));
        }
        if (walRequests.isEmpty()) {
            return;
        }
        
        // WAL
        List<WalEntry> appended = walService.appendBatch(walRequests);
        if (appended.isEmpty()) {
            return;
        }
        
        
        // apply 撮合結果
        for (MatchResult result : matchResults) {
            orderBook.apply(result);
            orderBook.markProcessed(result.getTakerOrder().getOrderId());
        }
        
        // 快照
        long lastSeq = appended.get(appended.size() - 1).getSeq();
        if (lastSeq > 0) {
            snapshotService.maybeSnapshot(lastSeq, orderBook, partitionOffsets);
        }
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
            orderBook.restoreProcessedIds(snapshot.getProcessedOrderIds());
        }
        return snapshot;
    }
    
    private void replayWal() {
        long startSeq = snapshotState != null ? snapshotState.getLastSeq() + 1 : 1L;
        List<WalEntry> entries = walService.readFrom(startSeq);
        for (WalEntry entry : entries) {
            try {
                orderBook.apply(entry.getMatchResult());
                orderBook.markProcessed(entry.getMatchResult().getTakerOrder().getOrderId());
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
    
    public record OrderWithOffset(Order order, long offset) {
    }
}
