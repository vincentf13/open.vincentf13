package open.vincentf13.exchange.matching.infra.wal.loader;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.matching.domain.match.result.Trade;
import open.vincentf13.exchange.matching.infra.MatchingEvent;
import open.vincentf13.exchange.matching.infra.persistence.repository.TradeRepository;
import open.vincentf13.exchange.matching.infra.wal.InstrumentWal;
import open.vincentf13.exchange.matching.infra.wal.WalEntry;
import open.vincentf13.exchange.matching.infra.wal.WalProgressStore;
import open.vincentf13.exchange.matching.service.InstrumentProcessor;
import open.vincentf13.exchange.matching.service.MatchingEngine;
import open.vincentf13.exchange.matching.sdk.mq.event.OrderBookUpdatedEvent;
import open.vincentf13.exchange.matching.sdk.mq.event.TradeExecutedEvent;
import open.vincentf13.exchange.matching.sdk.mq.topic.MatchingTopics;
import open.vincentf13.sdk.core.log.OpenLog;
import open.vincentf13.sdk.core.object.mapper.OpenObjectMapper;
import open.vincentf13.sdk.infra.mysql.mq.outbox.MqOutboxRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class WalLoader {
    
    private final MatchingEngine matchingEngine;
    private final WalProgressStore walProgressStore;
    private final TradeRepository tradeRepository;
    private final MqOutboxRepository outboxRepository;
    private final TransactionTemplate transactionTemplate;
    
    private final Map<Long, Long> lastProcessedSeqMap = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void init() {
    }
    
    @Scheduled(fixedDelayString = "${open.vincentf13.exchange.matching.loader-interval-ms:1000}")
    public void drainWal() {
        for (InstrumentProcessor processor : matchingEngine.getProcessors()) {
            drainInstrumentWal(processor);
        }
    }

    private void drainInstrumentWal(InstrumentProcessor processor) {
        Long instrumentId = processor.getInstrumentId();
        long lastSeq = lastProcessedSeqMap.computeIfAbsent(instrumentId, walProgressStore::loadLastProcessedSeq);

        InstrumentWal wal = processor.getWal();
        List<WalEntry> entries = wal.readFrom(lastSeq + 1);

        if (entries.isEmpty()) {
            return;
        }

        for (WalEntry entry : entries) {
            try {
                processEntry(entry);
                lastSeq = entry.getSeq();
            } catch (Exception ex) {
                OpenLog.error(MatchingEvent.WAL_LOADER_FAILED,
                              ex,
                              "seq",
                              entry.getSeq(),
                              "instrumentId", instrumentId);
                break;
            }
        }

        lastProcessedSeqMap.put(instrumentId, lastSeq);
        walProgressStore.saveLastProcessedSeq(instrumentId, lastSeq);
    }
    
    private void processEntry(WalEntry entry) {
        List<Trade> trades = entry.getMatchResult().getTrades();
        List<Trade> persisted = new ArrayList<>();
        long baseOffset = entry.getSeq() << 20;
        final long[] nextIndex = {0L};
        transactionTemplate.executeWithoutResult(status -> {
            if (!trades.isEmpty()) {
                try {
                    tradeRepository.batchInsert(trades);
                } catch (DuplicateKeyException ex) {
                    OpenLog.warn(MatchingEvent.TRADE_DUPLICATE, ex);
                }
                persisted.addAll(trades);
                nextIndex[0] = publishTrades(baseOffset, trades, nextIndex[0]);
            }
            if (entry.getOrderBookUpdatedEvent() != null) {
                publishOrderBook(baseOffset, entry.getOrderBookUpdatedEvent());
            }
        });
        OpenLog.info(MatchingEvent.WAL_ENTRY_APPLIED,
                     "seq", entry.getSeq(),
                     "tradeCount", persisted.size());
    }

    private long publishTrades(long baseOffset,
                               List<Trade> trades,
                               long startIndex) {
        long index = startIndex;
        for (Trade trade : trades) {
            long eventSeq = baseOffset + index++;
            try {
                outboxRepository.appendWithSeq(MatchingTopics.TRADE_EXECUTED.getTopic(),
                                               trade.getTradeId(),
                                               OpenObjectMapper.convert(trade, TradeExecutedEvent.class),
                                               null,
                                               eventSeq);
            } catch (DuplicateKeyException ex) {
                OpenLog.warn(MatchingEvent.OUTBOX_DUPLICATE_TRADE, ex);
            }
        }
        return index;
    }
    
    private void publishOrderBook(long seq,
                                  OrderBookUpdatedEvent event) {
        try {
            outboxRepository.appendWithSeq(MatchingTopics.ORDERBOOK_UPDATED.getTopic(),
                                    event.instrumentId(),
                                    event,
                                    null,
                                    seq);
        } catch (DuplicateKeyException ex) {
            OpenLog.warn(MatchingEvent.OUTBOX_DUPLICATE_ORDERBOOK, ex);
        }
    }
}
