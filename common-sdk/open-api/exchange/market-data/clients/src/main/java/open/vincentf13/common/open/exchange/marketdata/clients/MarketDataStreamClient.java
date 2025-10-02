package open.vincentf13.common.open.exchange.marketdata.clients;

import open.vincentf13.common.open.exchange.marketdata.interfaces.TradeEventDto;
import java.util.function.Consumer;

/**
 * Placeholder gRPC/WebSocket client for the market data stream.
 */
public interface MarketDataStreamClient {

    void subscribe(String symbol, Consumer<TradeEventDto> listener);
}
