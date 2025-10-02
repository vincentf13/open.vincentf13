package open.vincentf13.marketdata.publisher;

import open.vincentf13.api.marketdata.TradeEventDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Placeholder component broadcasting trade events to WebSocket/Kafka.
 */
@Component
public class MarketDataPublisher {

    private static final Logger log = LoggerFactory.getLogger(MarketDataPublisher.class);

    public void publish(TradeEventDto tradeEventDto) {
        log.info("Broadcasting trade event {}", tradeEventDto);
    }
}
