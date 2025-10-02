package open.vincentf13.common.open.exchange.matching.clients;

import open.vincentf13.common.open.exchange.matching.interfaces.OrderCommandDto;

/**
 * Client abstraction for submitting orders to the matching engine.
 */
public interface MatchingEngineClient {

    void submit(OrderCommandDto command);
}
