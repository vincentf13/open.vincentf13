package open.vincentf13.exchange.matching.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.matching.domain.order.book.Order;
import open.vincentf13.exchange.matching.infra.MatchingEvent;
import open.vincentf13.sdk.core.log.OpenLog;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class MatchingEngine {
    
    private final Map<Long, InstrumentProcessor> processors = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void init() {
        scanAndLoadInstruments();
    }

    @PreDestroy
    public void shutdown() {
        processors.values().forEach(InstrumentProcessor::shutdown);
    }
    
    public void processBatch(List<Order> batch) {
        if (batch == null || batch.isEmpty()) {
            return;
        }
        
        Map<Long, List<Order>> byInstrument = batch.stream()
            .collect(Collectors.groupingBy(Order::getInstrumentId));
            
        byInstrument.forEach((instrumentId, orders) -> {
            InstrumentProcessor processor = processors.computeIfAbsent(instrumentId, this::createProcessor);
            processor.getExecutor().execute(() -> {
                try {
                    processor.processBatch(orders);
                } catch (Exception e) {
                    OpenLog.error(MatchingEvent.ORDER_ROUTING_ERROR, e, "instrumentId", instrumentId);
                }
            });
        });
    }
    
    private InstrumentProcessor createProcessor(Long instrumentId) {
        InstrumentProcessor processor = new InstrumentProcessor(instrumentId);
        processor.init();
        return processor;
    }

    public Collection<InstrumentProcessor> getProcessors() {
        return processors.values();
    }
    
    private void scanAndLoadInstruments() {
        Path dir = Path.of("data/matching");
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile)
                  .map(Path::getFileName)
                  .map(Path::toString)
                  .filter(name -> name.startsWith("wal-") && name.endsWith(".wal"))
                  .map(name -> name.substring(4, name.length() - 4))
                  .map(Long::parseLong)
                  .distinct()
                  .forEach(instrumentId -> processors.computeIfAbsent(instrumentId, this::createProcessor));
        } catch (Exception e) {
             OpenLog.error(MatchingEvent.WAL_LOAD_FAILED, e, "action", "scan_instruments");
        }
    }
}
