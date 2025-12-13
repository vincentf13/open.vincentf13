package open.vincentf13.exchange.order.sdk.rest.api.enums;

/**
  訂單事件類型，對應 order_events.event_type 欄位。
 */
public enum OrderEventType {
    ORDER_CREATED,
    ORDER_SUBMITTED,
    ORDER_REJECTED,
    ORDER_TRADE_FILLED
}
