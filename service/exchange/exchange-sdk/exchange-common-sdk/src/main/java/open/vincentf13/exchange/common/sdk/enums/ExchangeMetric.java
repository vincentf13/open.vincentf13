package open.vincentf13.exchange.common.sdk.enums;

import open.vincentf13.sdk.core.metrics.IMetric;

/**
 * 定義 Exchange 業務層級的監控指標。
 */
public enum ExchangeMetric implements IMetric {

    /**
     * 下單請求統計。
     * <ul>
     *   <li><b>symbol</b>: 幣對 (如 "BTCUSDT")</li>
     *   <li><b>side</b>: 方向 ("BUY", "SELL")</li>
     *   <li><b>status</b>: 結果 ("success", "fail")</li>
     * </ul>
     */
    ORDER_REQUEST("exchange.order.request", "symbol", "side", "status"),

    /**
     * 撮合延遲 (Timer)。
     * <ul>
     *   <li><b>symbol</b>: 幣對</li>
     * </ul>
     */
    MATCHING_LATENCY("exchange.matching.latency", "symbol"),

    /**
     * 撮合成交統計。
     * <ul>
     *   <li><b>symbol</b>: 幣對</li>
     * </ul>
     */
    MATCHING_TRADE("exchange.matching.trade", "symbol"),

    /**
     * 帳戶餘額更新頻率。
     * <ul>
     *   <li><b>asset</b>: 資產名稱 (如 "USDT")</li>
     * </ul>
     */
    ACCOUNT_BALANCE_UPDATE("exchange.account.balance.update", "asset");

    private final String name;
    private final String[] tagKeys;

    ExchangeMetric(String name, String... tagKeys) {
        this.name = name;
        this.tagKeys = tagKeys;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String[] getTagKeys() {
        return tagKeys;
    }
}
