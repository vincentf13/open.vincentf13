package open.vincentf13.exchange.matching.infra.wal;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.matching.domain.match.result.MatchResult;
import open.vincentf13.exchange.matching.sdk.mq.event.OrderBookUpdatedEvent;
import open.vincentf13.exchange.matching.infra.MatchingEvent;
import open.vincentf13.sdk.core.log.OpenLog;
import open.vincentf13.sdk.core.object.mapper.OpenObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class WalService {
    
    private static final Path WAL_PATH = Path.of("data/matching/engine.wal");
    @Getter
    private final List<WalEntry> entries = Collections.synchronizedList(new ArrayList<>());
    private final AtomicLong lastSeq = new AtomicLong(0L);
    
    public synchronized void loadExisting() {
        if (!Files.exists(WAL_PATH)) {
            return;
        }
        try (Stream<String> lines = Files.lines(WAL_PATH)) {
            lines.filter(line -> !line.isBlank())
                 .map(this::parseEntry)
                 .forEach(entry -> {
                     entries.add(entry);
                     lastSeq.set(Math.max(lastSeq.get(), entry.getSeq()));
                 });
        } catch (IOException ex) {
            OpenLog.error(MatchingEvent.WAL_LOAD_FAILED, ex);
        }
    }
    
    public synchronized WalEntry append(MatchResult result,
                                        OrderBookUpdatedEvent orderBookUpdatedEvent,
                                        long kafkaOffset,
                                        int partition) {
        long nextSeq = lastSeq.incrementAndGet();
        WalEntry entry = WalEntry.builder()
                                 .seq(nextSeq)
                                 .kafkaOffset(kafkaOffset)
                                 .partition(partition)
                                 .matchResult(result)
                                 .orderBookUpdatedEvent(orderBookUpdatedEvent)
                                 .appendedAt(Instant.now())
                                 .build();
        writeLine(OpenObjectMapper.toJson(entry));
        entries.add(entry);
        return entry;
    }
    
    public List<WalEntry> readFrom(long startSeq) {
        synchronized (entries) {
            return entries.stream()
                          .filter(entry -> entry.getSeq() >= startSeq)
                          .toList();
        }
    }
    
    public long latestSeq() {
        return lastSeq.get();
    }
    
    public synchronized void truncate() {
        entries.clear();
        lastSeq.set(0L);
        try {
            Files.deleteIfExists(WAL_PATH);
        } catch (IOException ex) {
            OpenLog.error(MatchingEvent.WAL_TRUNCATE_FAILED, ex);
        }
    }
    
    private WalEntry parseEntry(String line) {
        return OpenObjectMapper.fromJson(line, WalEntry.class);
    }
    
    private void writeLine(String json) {
        try {
            Files.createDirectories(WAL_PATH.getParent());
            Files.writeString(WAL_PATH,
                              json + System.lineSeparator(),
                              StandardOpenOption.CREATE,
                              StandardOpenOption.APPEND,
                              StandardOpenOption.SYNC);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to append WAL", ex);
        }
    }
}
