package open.vincentf13.sdk.core.metrics;

/**
 * 定義系統層級通用的監控指標。
 * 用於標準化各個微服務的基礎監控行為。
 */
public enum SysMetric implements IMetric {

    /**
     * 系統錯誤統計。
     * <p>
     * Tags:
     * <ul>
     *   <li><b>type</b>: 錯誤類型 (如 "db_timeout", "npe", "validation_error")</li>
     *   <li><b>module</b>: 發生的模組或組件名稱</li>
     * </ul>
     */
    ERROR("sys.error", "type", "module"),

    /**
     * 外部依賴呼叫統計 (Timer)。
     * <p>
     * Tags:
     * <ul>
     *   <li><b>target</b>: 目標服務名稱 (如 "payment_gateway", "google_api")</li>
     *   <li><b>status</b>: 呼叫結果狀態 (如 "success", "timeout", "500")</li>
     * </ul>
     */
    DEPENDENCY_CALL("sys.dependency.call", "target", "status"),

    /**
     * 應用層本地緩存統計。
     * <p>
     * Tags:
     * <ul>
     *   <li><b>name</b>: 緩存名稱 (如 "user_token_cache")</li>
     *   <li><b>result</b>: 操作結果 ("hit", "miss")</li>
     * </ul>
     */
    CACHE("sys.cache", "name", "result"),

    /**
     * 關鍵系統事件計數。
     * <p>
     * Tags:
     * <ul>
     *   <li><b>name</b>: 事件名稱 (如 "startup", "config_reload", "leader_election")</li>
     * </ul>
     */
    EVENT("sys.event", "name");

    private final String name;
    private final String[] tagKeys;

    SysMetric(String name, String... tagKeys) {
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
