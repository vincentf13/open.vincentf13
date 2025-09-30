package com.example.exchange.matching.engine;

import com.example.exchange.api.matching.OrderCommandDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Simplified matching engine placeholder.
 */
@Component
public class MatchingEngine {

    private static final Logger log = LoggerFactory.getLogger(MatchingEngine.class);

    public void process(OrderCommandDto command) {
        log.debug("Received order {}", command);
    }
}
