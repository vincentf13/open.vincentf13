package open.vincentf13.service.spot.infra.aeron;

/**
 * Aeron 傳輸層物理常數
 */
public class AeronConstants {
    
    /** 指標統計批次大小 */
    public static final int METRICS_BATCH_SIZE = 5000;

    /** Aeron 訂閱輪詢限制 */
    public static final int AERON_POLL_LIMIT = 10;

    /** RESUME 握手信號的總長度 */
    public static final int RESUME_SIGNAL_LENGTH = 12;

    /** RESUME 握手信號發送間隔 (ms) */
    public static final int RESUME_SIGNAL_INTERVAL_MS = 200;

    /** 訊息序列號在 DirectBuffer 中的偏移量 */
    public static final int MSG_SEQ_OFFSET = 4;

    /** 進度持久化週期 */
    public static final int METADATA_FLUSH_PERIOD = 100;

    /** 背壓重試次數限制 */
    public static final int BACK_PRESSURE_RETRY_LIMIT = 1000;

    /** 預設 Term Buffer 長度 (64MB) */
    public static final int DEFAULT_TERM_BUFFER_LENGTH = 64 * 1024 * 1024;
    public static final int WAL_BATCH_SIZE = 1000 ;
    
    /** 
      Aeron 通訊組件工作狀態 (AeronState)
     */
    public enum AeronState {
        WAITING, // 等待握手/同步中
        SENDING  // 正常發送數據中
    }

    private AeronConstants() {}
}
