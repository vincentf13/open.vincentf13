package open.vincentf13.sdk.core.metrics;

import open.vincentf13.sdk.core.metrics.enums.IMetric;

import java.util.Arrays;

/**
 * 負責驗證指標參數的正確性。
 */
final class MetricValidator {

    private MetricValidator() {}

    /**
     * 驗證傳入的 Tags 是否符合 IMetric 的定義。
     * <p>
     * 規則：
     * 1. 如果 IMetric 沒有定義 Tag Keys，則不檢查。
     * 2. 傳入的 Tags 數量必須是定義 Keys 數量的兩倍 (Key + Value)。
     * 3. 傳入的 Tag Keys 必須與定義的 Keys 完全匹配 (順序與名稱)。
     *
     * @param metric 指標定義
     * @param tags   傳入的 Tag 陣列 (key, value 交錯)
     * @throws IllegalArgumentException 如果驗證失敗
     */
    static void validate(IMetric metric, String... tags) {
        // 如果全域驗證開關關閉，則直接返回 (由 Metrics 類別控制，這裡先預留邏輯，或者每次都檢查)
        // 為了效能，我們可以在 Metrics 設一個 static boolean。
        if (!Metrics.isValidationEnabled()) {
            return;
        }

        String[] requiredKeys = metric.getTagKeys();
        if (requiredKeys == null || requiredKeys.length == 0) {
            return;
        }

        // 1. 檢查數量
        int expectedLen = requiredKeys.length * 2;
        int actualLen = tags == null ? 0 : tags.length;
        
        if (actualLen != expectedLen) {
            throw new IllegalArgumentException(
                String.format("Metric '%s' tag count mismatch. Expected %d tags (%s) but got %d.",
                    metric.getName(), requiredKeys.length, Arrays.toString(requiredKeys), actualLen / 2));
        }

        // 2. 檢查 Key 是否匹配 (嚴格檢查順序)
        for (int i = 0; i < requiredKeys.length; i++) {
            String expectedKey = requiredKeys[i];
            String actualKey = tags[i * 2];
            
            if (!expectedKey.equals(actualKey)) {
                throw new IllegalArgumentException(
                    String.format("Metric '%s' tag mismatch at index %d. Expected key '%s' but got '%s'.",
                        metric.getName(), i, expectedKey, actualKey));
            }
        }
    }
}
