package open.vincentf13.sdk.core.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.Objects;

/**
 * Metrics 核心類別，負責 MeterRegistry 的初始化與管理。
 * <p>
 * 具體的指標操作請使用：
 * <ul>
 *   <li>{@link MCounter}: 計數器</li>
 *   <li>{@link MTimer}: 短任務計時</li>
 *   <li>{@link MLongTaskTimer}: 長任務計時</li>
 *   <li>{@link MSummary}: 數值分佈統計</li>
 *   <li>{@link MGauge}: 即時數值與執行緒池監控</li>
 * </ul>
 */
public final class Metrics {

    private static MeterRegistry REG;
    private static volatile boolean validationEnabled = true;

    private Metrics() {}

    /**
     * 設定是否啟用指標 Tag 驗證。
     * 建議在生產環境 (Prod) 可以關閉以提升些微效能，但在開發環境 (Dev/Test) 開啟以確保規範。
     *
     * @param enabled 是否啟用
     */
    public static void setValidationEnabled(boolean enabled) {
        validationEnabled = enabled;
    }

    /**
     * 檢查是否啟用指標 Tag 驗證。
     */
    public static boolean isValidationEnabled() {
        return validationEnabled;
    }

    /**
     * 初始化 Metrics 系統。
     * 通常由 Spring Boot 配置類別呼叫。
     *
     * @param registry MeterRegistry 實例
     * @param app      應用程式名稱
     * @param env      環境名稱 (如 dev, prod)
     */
    public static void init(MeterRegistry registry, String app, String env) {
        // 驗證並將傳入的 MeterRegistry 賦值給全域靜態變數 REG，若為 null 則拋出 NullPointerException
        REG = Objects.requireNonNull(registry);
        // 設定所有指標的通用標籤 (Common Tags)，這裡會自動為每個指標附帶 app (應用程式名稱) 和 env (環境) 標籤
        REG.config().commonTags("app", app, "env", env);
    }

    /**
     * 取得全域 MeterRegistry 實例。
     * 供內部指標工具類使用。
     *
     * @return MeterRegistry 實例
     */
    public static MeterRegistry getRegistry() {
        if (REG == null) {
            throw new IllegalStateException("Metrics not initialized. Call Metrics.init() first.");
        }
        return REG;
    }

    /**
     * 產生指標緩存用的 Key。
     * 格式: name|tag1,tag2,...
     *
     * @param name 指標名稱
     * @param tags 標籤陣列
     * @return 唯一的 Key 字串
     */
    public static String key(String name, String... tags) {
        return name + "|" + String.join(",", tags == null ? new String[0] : tags);
    }
}