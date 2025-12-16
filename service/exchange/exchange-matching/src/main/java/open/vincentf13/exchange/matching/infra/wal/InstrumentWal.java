package open.vincentf13.exchange.matching.infra.wal;

import lombok.Getter;
import open.vincentf13.exchange.matching.domain.match.result.MatchResult;
import open.vincentf13.exchange.matching.infra.MatchingEvent;
import open.vincentf13.exchange.matching.sdk.mq.event.OrderBookUpdatedEvent;
import open.vincentf13.sdk.core.log.OpenLog;
import open.vincentf13.sdk.core.object.mapper.OpenObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

public class InstrumentWal {

    private final Path walPath;
    @Getter
    private final Long instrumentId;
    @Getter
    private final List<WalEntry> entries = new ArrayList<>();
    private final AtomicLong lastSeq = new AtomicLong(0L);

    public InstrumentWal(Long instrumentId) {
        this.instrumentId = instrumentId;
        this.walPath = Path.of("data/matching/wal-" + instrumentId + ".wal");
    }

    public synchronized void loadExisting() {
        if (!Files.exists(walPath)) {
            return;
        }
        try (Stream<String> lines = Files.lines(walPath)) {
            lines.filter(line -> !line.isBlank())
                 .map(this::parseEntry)
                 .forEach(entry -> {
                     entries.add(entry);
                     lastSeq.set(Math.max(lastSeq.get(), entry.getSeq()));
                 });
        } catch (IOException ex) {
            OpenLog.error(MatchingEvent.WAL_LOAD_FAILED, ex, "instrumentId", instrumentId);
        }
    }

    public synchronized List<WalEntry> appendBatch(List<WalAppendRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        StringBuilder sb = new StringBuilder(requests.size() * 256);
        List<WalEntry> appended = new ArrayList<>(requests.size());
        for (WalAppendRequest req : requests) {
            long nextSeq = lastSeq.incrementAndGet();
            WalEntry entry = WalEntry.builder()
                                     .seq(nextSeq)
                                     .matchResult(req.result())
                                     .orderBookUpdatedEvent(req.orderBookUpdatedEvent())
                                     .appendedAt(Instant.now())
                                     .build();
            sb.append(OpenObjectMapper.toJson(entry)).append(System.lineSeparator());
            appended.add(entry);
            entries.add(entry);
        }
        writeLine(sb.toString());
        return appended;
    }

    public synchronized List<WalEntry> readFrom(long startSeq) {
        return entries.stream()
                      .filter(entry -> entry.getSeq() >= startSeq)
                      .sorted(Comparator.comparingLong(WalEntry::getSeq))
                      .toList();
    }
    

    private WalEntry parseEntry(String line) {
        return OpenObjectMapper.fromJson(line, WalEntry.class);
    }

    private void writeLine(String json) {
        try {
            if (walPath.getParent() != null) {
                Files.createDirectories(walPath.getParent());
            }
            Files.writeString(walPath,
                              json,
                              StandardOpenOption.CREATE,
                              StandardOpenOption.APPEND,
                              StandardOpenOption.SYNC);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to append WAL for " + instrumentId, ex);
        }
    }

    public record WalAppendRequest(MatchResult result,
                                   OrderBookUpdatedEvent orderBookUpdatedEvent) {
    }
}
