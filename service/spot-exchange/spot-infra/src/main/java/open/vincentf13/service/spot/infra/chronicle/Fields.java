package open.vincentf13.service.spot.infra.chronicle;

import net.openhft.chronicle.wire.WireKey;

/** 
  系統內部數據交換欄位定義 (建模版)
  用於所有 Chronicle Queue (WAL) 的 Key，提供編譯期檢查與極致效能
 */
public enum Fields implements WireKey {
    msgType,    // 訊息類型 (int)
    payload,    // 二進制載體 (Bytes)
    aeronSeq,   // Aeron 傳輸序號 (long)
    userId,     // 用戶 ID (long)
    topic,      // 業務主題 (String)
    data,       // JSON 數據體 (String)
    timestamp;  // 時間戳 (long)
}
