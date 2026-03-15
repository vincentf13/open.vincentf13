package open.vincentf13.service.spot.matching.monitor;

import lombok.extern.slf4j.Slf4j;
import open.vincentf13.service.spot.matching.engine.OrderBook;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 異步指標監控 (基於日誌)
 * 每秒在日誌中輸出 TPS 資訊，不佔用 Web 請求線程，
 * 是極致性能環境下的推薦監控方式。
 */
@Slf4j
@Component
@EnableScheduling
public class AsyncMetricsLogger {

    private final AtomicLong lastMatchCount = new AtomicLong(0);

    @Scheduled(fixedRate = 1000)
    public void logTps() {
        long currentMatches = OrderBook.TOTAL_MATCH_COUNT.get();
        long tps = currentMatches - lastMatchCount.get();
        lastMatchCount.set(currentMatches);
        
        if (tps > 0) {
            log.info("[METRICS] Current TPS: {}, Total Matches: {}", tps, currentMatches);
        }
    }
}
