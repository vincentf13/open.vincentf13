package com.example.exchange.funding.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Placeholder scheduler that would calculate funding rates and emit ledger events.
 */
@Component
public class FundingRateScheduler {

    private static final Logger log = LoggerFactory.getLogger(FundingRateScheduler.class);

    @Scheduled(fixedDelayString = "PT1H")
    public void calculateFundingRates() {
        log.info("Running funding rate calculation");
    }
}
