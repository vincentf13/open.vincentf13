package com.example.exchange.client.matching;

import com.example.exchange.api.matching.OrderCommandDto;

/**
 * Client abstraction for submitting orders to the matching engine.
 */
public interface MatchingEngineClient {

    void submit(OrderCommandDto command);
}
