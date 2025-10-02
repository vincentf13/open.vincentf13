package open.vincentf13.client.marketdata;

import open.vincentf13.api.marketdata.TradeEventDto;
import java.util.function.Consumer;

/**
 * Placeholder gRPC/WebSocket client for the market data stream.
 */
public interface MarketDataStreamClient {

    void subscribe(String symbol, Consumer<TradeEventDto> listener);
}
