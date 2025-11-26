package open.vincentf13.exchange.common.sdk.constants;

/*
 * 交易相關的常用十進位數值限制，提供 enum 與 Names 供註解/欄位引用。
 */
public enum ValidationConstant {
    PRICE_MIN(Values.PRICE_MIN),
    QUANTITY_MIN(Values.QUANTITY_MIN),
    FEE_MIN(Values.FEE_MIN);

    private final String value;

    ValidationConstant(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static final class Values {
        public static final String PRICE_MIN = "0.00000001";
        public static final String QUANTITY_MIN = "0.00000001";
        public static final String FEE_MIN = "0.00000000";

        private Values() {
        }
    }
}
