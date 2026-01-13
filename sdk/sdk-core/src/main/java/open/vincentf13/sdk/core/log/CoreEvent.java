package open.vincentf13.sdk.core.log;

/*
 * Core 模組事件。
 */
public enum CoreEvent implements OpenEvent {
  STARTUP_CACHE_LOADING("CoreStartupCacheLoading", "Starting cache loading"),
  STARTUP_CACHE_LOADED("CoreStartupCacheLoaded", "Cache loaded successfully"),
  STARTUP_CACHE_LOAD_FAILED("CoreStartupCacheLoadFailed", "Cache loading failed"),
  DEFAULTS_RESOURCE_MISSING("DefaultsResourceMissing", "Defaults resource not found"),
  DEFAULTS_APPLIED("DefaultsApplied", "Defaults applied from resource");

  private final String event;
  private final String message;

  CoreEvent(String event, String message) {
    this.event = event;
    this.message = message;
  }

  @Override
  public String event() {
    return event;
  }

  @Override
  public String message() {
    return message;
  }
}
