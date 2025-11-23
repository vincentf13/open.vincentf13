package open.vincentf13.exchange.position.sdk.rest.api.enums;

/**
 * 操作意圖類型
 * 用於描述使用者對倉位的操作方向
 */
public enum PositionIntentType {

    /**
     * 增加倉位
     * 代表開新倉或加碼既有倉位
     */
    INCREASE,

    /**
     * 減少倉位
     * 部分平倉、減碼
     */
    REDUCE,

    /**
     * 完全平倉
     * 將當前持倉全部關閉
     */
    CLOSE;

    /**
     * 是否需要預留倉位數量（例如減倉、平倉需要檢查持倉是否足夠）
     */
    public boolean requiresPositionReservation() {
        return this == REDUCE || this == CLOSE;
    }
}
