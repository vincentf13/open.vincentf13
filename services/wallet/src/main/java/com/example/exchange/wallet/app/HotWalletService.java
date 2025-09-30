package com.example.exchange.wallet.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Placeholder for hot wallet orchestration and signing flows.
 */
@Service
public class HotWalletService {

    private static final Logger log = LoggerFactory.getLogger(HotWalletService.class);

    public void requestWithdrawal(String requestId) {
        log.info("Initiating withdrawal {}", requestId);
    }
}
