package open.vincentf13.exchange.matching.infra.loader;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.matching.domain.model.Trade;
import open.vincentf13.exchange.matching.infra.outbox.MatchingOutboxRepository;
import open.vincentf13.exchange.matching.infra.persistence.repository.TradeRepository;
import open.vincentf13.exchange.matching.infra.wal.WalEntry;
import open.vincentf13.exchange.matching.infra.wal.WalProgressStore;
import open.vincentf13.exchange.matching.infra.wal.WalService;
import open.vincentf13.exchange.matching.sdk.mq.enums.TradeType;
import open.vincentf13.exchange.matching.sdk.mq.event.OrderBookUpdatedEvent;
import open.vincentf13.exchange.matching.sdk.mq.event.TradeExecutedEvent;
import open.vincentf13.exchange.matching.sdk.mq.topic.MatchingTopics;
import open.vincentf13.sdk.core.log.OpenLog;
import open.vincentf13.sdk.core.object.mapper.OpenObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class WalLoader {
    
    private final WalService walService;
    private final WalProgressStore walProgressStore;
    private final TradeRepository tradeRepository;
    private final MatchingOutboxRepository outboxRepository;
    private final TransactionTemplate transactionTemplate;
    
    private volatile long lastProcessedSeq;
    
    @PostConstruct
    public void init() {
        this.lastProcessedSeq = walProgressStore.loadLastProcessedSeq();
    }
    
    @Scheduled(fixedDelayString = "${open.vincentf13.exchange.matching.loader-interval-ms:1000}")
    public void drainWal() {
        List<WalEntry> entries = walService.readFrom(lastProcessedSeq + 1);
        if (entries.isEmpty()) {
            return;
        }
        for (WalEntry entry : entries) {
            try {
                processEntry(entry);
                lastProcessedSeq = entry.getSeq();
            } catch (Exception ex) {
                OpenLog.error(MatchingLogEvent.WAL_LOADER_FAILED,
                              ex,
                              "seq",
                              entry.getSeq());
                break;
            }
        }
        walProgressStore.saveLastProcessedSeq(lastProcessedSeq);
    }
    
    private void processEntry(WalEntry entry) {
        List<Trade> trades = entry.getMatchResult().getTrades();
        List<Trade> persisted = new ArrayList<>();
        transactionTemplate.executeWithoutResult(status -> {
            if (!trades.isEmpty()) {
                try {
                    tradeRepository.batchInsert(trades);
                } catch (DuplicateKeyException ex) {
                    OpenLog.warn(MatchingLogEvent.TRADE_DUPLICATE, ex);
                }
                persisted.addAll(trades);
                publishTrades(entry.getSeq(), trades);
            }
            if (entry.getOrderBookUpdatedEvent() != null) {
                publishOrderBook(entry.getSeq(), entry.getOrderBookUpdatedEvent());
            }
        });
        OpenLog.info(MatchingLogEvent.WAL_ENTRY_APPLIED,
                     "seq", entry.getSeq(),
                     "tradeCount", persisted.size());
    }
    
    private void publishTrades(long baseSeq,
                               List<Trade> trades) {
        long seq = baseSeq * 10;
        for (Trade trade : trades) {
            long eventSeq = seq++;
            trade.setTradeType(trade.getTradeType() == null ? TradeType.NORMAL : trade.getTradeType());
            try {
                outboxRepository.append(MatchingTopics.TRADE_EXECUTED.getTopic(),
                                        trade.getTradeId(),
                                        OpenObjectMapper.convert(trade, TradeExecutedEvent.class),
                                        null,
                                        eventSeq);
            } catch (DuplicateKeyException ex) {
                OpenLog.warn(MatchingLogEvent.OUTBOX_DUPLICATE_TRADE, ex);
            }
        }
    }
    
    private void publishOrderBook(long seq,
                                  OrderBookUpdatedEvent event) {
        try {
            outboxRepository.append(MatchingTopics.ORDERBOOK_UPDATED.getTopic(),
                                    event.instrumentId(),
                                    event,
                                    null,
                                    seq * 10 + 9);
        } catch (DuplicateKeyException ex) {
            OpenLog.warn(MatchingLogEvent.OUTBOX_DUPLICATE_ORDERBOOK, ex);
        }
    }
}
