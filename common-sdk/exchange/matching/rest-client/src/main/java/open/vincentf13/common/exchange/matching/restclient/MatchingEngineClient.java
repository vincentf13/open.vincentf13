package open.vincentf13.common.exchange.matching.restclient;

import open.vincentf13.common.exchange.matching.restapi.OrderCommandDto;

/**
 * Client abstraction for submitting orders to the matching engine.
 */
public interface MatchingEngineClient {

    void submit(OrderCommandDto command);
}
