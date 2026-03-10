package open.vincentf13.service.spot_exchange.infra;

/** 
  全系統核心常量定義
 */
public class ExchangeConstants {
    // --- 精度控制 ---
    public static final long SCALE = 100_000_000L; // 10^8

    // --- Aeron 頻道定義 ---
    public static final String INBOUND_CHANNEL = "aeron:udp?endpoint=localhost:40444";
    public static final String OUTBOUND_CHANNEL = "aeron:udp?endpoint=localhost:40445";
    public static final int INBOUND_STREAM_ID = 10;
    public static final int OUTBOUND_STREAM_ID = 30;

    // --- 系統保留 ID ---
    public static final long SYSTEM_USER_ID = 0L;
    public static final long ID_REJECTED = 0L;

    // --- 元數據 Key (Byte) ---
    public static final byte CORE_PROGRESS_KEY = 1;
    public static final byte GW_INBOUND_PROGRESS_KEY = 2;
    public static final byte GW_OUTBOUND_PROGRESS_KEY = 3;
    
    // --- 資產 ID 映射範例 ---
    public static final int ASSET_BTC = 1;
    public static final int ASSET_USDT = 2;
}
