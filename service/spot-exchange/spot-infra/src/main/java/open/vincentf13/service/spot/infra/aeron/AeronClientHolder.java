package open.vincentf13.service.spot.infra.aeron;

import io.aeron.Aeron;

/**
 * Aeron 全局物件持有者
 * 職責：管理 JVM 級別唯一的 Aeron 實例，提供靜態訪問入口
 */
public class AeronClientHolder {

    private static Aeron SHARED_AERON;

    /** 獲取全局唯一的 Aeron 實例 */
    public static Aeron aeron() {
        return SHARED_AERON;
    }

    /** 內部方法：由 Config 呼叫以注入實例 */
    static void setAeron(Aeron aeron) {
        SHARED_AERON = aeron;
    }

    private AeronClientHolder() {}
}
