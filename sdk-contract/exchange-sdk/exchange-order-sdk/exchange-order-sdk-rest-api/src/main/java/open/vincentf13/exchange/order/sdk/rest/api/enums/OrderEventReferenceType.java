package open.vincentf13.exchange.order.sdk.rest.api.enums;

/**
  訂單事件關聯來源，對應 order_events.reference_type 欄位。
 */
public enum OrderEventReferenceType {
    REQUEST,
    RISK_CHECK,
    ACCOUNT_EVENT,
    TRADE
}
