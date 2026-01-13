package open.vincentf13.exchange.matching.infra;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import open.vincentf13.sdk.core.log.OpenEvent;

@Getter
@RequiredArgsConstructor
public enum MatchingEvent implements OpenEvent {
  WAL_LOADER_FAILED("WAL_LOADER_FAILED", "WAL loader failed"),
  TRADE_DUPLICATE("TRADE_DUPLICATE", "Trade duplicate during batch insert"),
  WAL_ENTRY_APPLIED("WAL_ENTRY_APPLIED", "WAL entry applied to DB"),
  OUTBOX_DUPLICATE_TRADE("OUTBOX_DUPLICATE_TRADE", "Outbox duplicate trade event"),
  OUTBOX_DUPLICATE_ORDERBOOK("OUTBOX_DUPLICATE_ORDERBOOK", "Outbox duplicate order book event"),
  WAL_LOAD_FAILED("WAL_LOAD_FAILED", "Load WAL file failed"),
  WAL_TRUNCATE_FAILED("WAL_TRUNCATE_FAILED", "Truncate WAL failed"),
  WAL_PROGRESS_LOAD_FAILED("WAL_PROGRESS_LOAD_FAILED", "Load WAL progress failed"),
  WAL_PROGRESS_SAVE_FAILED("WAL_PROGRESS_SAVE_FAILED", "Save WAL progress failed"),
  SNAPSHOT_LOAD_FAILED("SNAPSHOT_LOAD_FAILED", "Load snapshot failed"),
  SNAPSHOT_WRITE_FAILED("SNAPSHOT_WRITE_FAILED", "Write snapshot failed"),
  WAL_REPLAY_FAILED("WAL_REPLAY_FAILED", "Replay WAL failed"),
  ORDER_ROUTING_ERROR("ORDER_ROUTING_ERROR", "Order routed to wrong instrument processor"),
  STARTUP_LOADING_INSTRUMENTS("MatchingStartupLoadingInstruments", "Loading instruments"),
  STARTUP_CACHE_LOAD_PARTIAL("MatchingStartupCacheLoadPartial", "Cache loaded partially"),
  STARTUP_INSTRUMENTS_LOADED("MatchingStartupInstrumentsLoaded", "Instruments loaded");

  private final String event;
  private final String message;

  @Override
  public String event() {
    return event;
  }

  @Override
  public String message() {
    return message;
  }
}
