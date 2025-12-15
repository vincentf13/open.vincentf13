package open.vincentf13.exchange.matching.infra.wal;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.matching.domain.book.OrderBook;
import open.vincentf13.exchange.matching.domain.model.MatchingOrder;
import open.vincentf13.sdk.core.log.OpenLog;
import open.vincentf13.sdk.core.object.mapper.OpenObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Component
@RequiredArgsConstructor
public class SnapshotService {
    
    private static final Path SNAPSHOT_PATH = Path.of("data/matching/snapshot.json");
    private final AtomicLong lastSnapshotSeq = new AtomicLong(0L);
    private static final long SNAPSHOT_INTERVAL = 1_000L;
    
    public SnapshotState load() {
        if (!Files.exists(SNAPSHOT_PATH)) {
            return null;
        }
        try {
            String json = Files.readString(SNAPSHOT_PATH);
            SnapshotState state = OpenObjectMapper.fromJson(json, SnapshotState.class);
            if (state != null) {
                lastSnapshotSeq.set(state.getLastSeq());
            }
            return state;
        } catch (IOException ex) {
            OpenLog.error(OpenLog.event("SNAPSHOT_LOAD_FAILED"), ex);
            return null;
        }
    }
    
    public void maybeSnapshot(long currentSeq,
                              OrderBook orderBook) {
        if (currentSeq - lastSnapshotSeq.get() < SNAPSHOT_INTERVAL) {
            return;
        }
        writeSnapshot(currentSeq, orderBook);
    }
    
    public void writeSnapshot(long currentSeq,
                              OrderBook orderBook) {
        List<MatchingOrder> openOrders = orderBook.dumpOpenOrders();
        SnapshotState state = SnapshotState.builder()
                                           .lastSeq(currentSeq)
                                           .openOrders(openOrders)
                                           .createdAt(Instant.now())
                                           .build();
        try {
            Files.createDirectories(SNAPSHOT_PATH.getParent());
            Files.writeString(SNAPSHOT_PATH, OpenObjectMapper.toPrettyJson(state));
            lastSnapshotSeq.set(currentSeq);
        } catch (IOException ex) {
            OpenLog.error(OpenLog.event("SNAPSHOT_WRITE_FAILED"), ex);
        }
    }
}
