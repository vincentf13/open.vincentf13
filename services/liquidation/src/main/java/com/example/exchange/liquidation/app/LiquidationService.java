package com.example.exchange.liquidation.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Placeholder service coordinating liquidation workflows.
 */
@Service
public class LiquidationService {

    private static final Logger log = LoggerFactory.getLogger(LiquidationService.class);

    public void liquidate(String accountId) {
        log.warn("Liquidation triggered for {}", accountId);
    }
}
