package open.vincentf13.exchange.common.sdk.enums;

public enum PlatformAccountCategory {
    ASSET,
    LIABILITY,
    REVENUE,
    EXPENSE,
    EQUITY;

    public String code() {
        return name();
    }
}
