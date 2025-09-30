package com.example.exchange.api.marketdata;

import java.time.Instant;

/**
 * DTO emitted by the market data service and consumed by downstream components.
 */
public record TradeEventDto(String symbol, String price, String quantity, Instant executedAt) {
}
