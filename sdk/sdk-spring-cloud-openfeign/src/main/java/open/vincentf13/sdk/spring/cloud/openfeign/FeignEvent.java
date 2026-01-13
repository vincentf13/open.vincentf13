package open.vincentf13.sdk.spring.cloud.openfeign;

import open.vincentf13.sdk.core.log.OpenEvent;

/** Feign 相關事件枚舉。 */
public enum FeignEvent implements OpenEvent {
  FEIGN_REQUEST("FeignRequest", "Feign request sent"),
  FEIGN_RESPONSE("FeignResponse", "Feign response received"),
  FEIGN_RETRYABLE_EXCEPTION("FeignRetryableException", "Retryable remote call failure"),
  FEIGN_EXCEPTION("FeignException", "Feign call failed"),
  FEIGN_DECODE_EXCEPTION("FeignDecodeException", "Failed to decode remote response"),
  FEIGN_ENCODE_EXCEPTION("FeignEncodeException", "Failed to encode remote request");

  private final String event;
  private final String message;

  FeignEvent(String event, String message) {
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
