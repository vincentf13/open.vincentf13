package open.vincentf13.exchange.common.sdk.enums;

public enum PositionSide {
  LONG,
  SHORT;

  public static PositionSide fromOrderSide(OrderSide orderSide) {
    if (orderSide == null) {
      return null;
    }
    return orderSide == OrderSide.BUY ? PositionSide.LONG : PositionSide.SHORT;
  }
}
