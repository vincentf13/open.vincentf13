package open.vincentf13.client.matching;

import open.vincentf13.api.matching.OrderCommandDto;

/**
 * Client abstraction for submitting orders to the matching engine.
 */
public interface MatchingEngineClient {

    void submit(OrderCommandDto command);
}
