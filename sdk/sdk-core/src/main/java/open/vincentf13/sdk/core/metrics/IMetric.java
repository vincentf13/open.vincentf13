package open.vincentf13.sdk.core.metrics;

/**
 * 定義指標的介面，建議使用 Enum 實作此介面以管理指標名稱與標籤。
 */
public interface IMetric {

    /**
     * 取得指標名稱。
     *
     * @return 指標名稱 (例如 "order.created")
     */
    String getName();

    /**
     * 取得該指標允許或必須包含的 Tag Keys。
     * <p>
     * 用於執行時期驗證，確保傳入的 Tags 符合預期。
     * 
     * @return Tag Key 陣列，若回傳 null 或空陣列則不進行驗證。
     */
    default String[] getTagKeys() {
        return null;
    }
}
