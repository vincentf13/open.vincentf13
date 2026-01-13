package open.vincentf13.exchange.common.sdk.constants;

/** 共用驗證常數：依欄位語意提供對應的最小值定義，並提供 enum/Names 供註解引用。 */
public enum ValidationConstant {
  PRICE_MIN(Names.PRICE_MIN),
  QUANTITY_MIN(Names.QUANTITY_MIN),
  AMOUNT_MIN(Names.AMOUNT_MIN),
  FEE_MIN(Names.FEE_MIN),
  NON_NEGATIVE(Names.NON_NEGATIVE);

  private final String value;

  ValidationConstant(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public static final class Names {
    public static final String PRICE_MIN = "0.00000001";
    public static final String QUANTITY_MIN = "0.00000001";
    public static final String AMOUNT_MIN = "0.00000001";
    public static final String FEE_MIN = "0.00000000";
    public static final String NON_NEGATIVE = "0.00000000";

    public static final int COMMON_SCALE = 12;
    public static final int MARGIN_RATIO_SCALE = 4;

    private Names() {}
  }
}
