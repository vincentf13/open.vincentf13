package open.vincentf13.service.spot_exchange.infra;

/** 
  全系統核心常量 (Review 基準)
 */
public class ExchangeConstants {
    public static final long SCALE = 100_000_000L;
    public static final long SYSTEM_USER_ID = 0L;
    public static final long ID_REJECTED = 0L;

    // --- 唯一元數據位點 Key (防止衝突) ---
    public static final byte PK_CORE_PROGRESS = 1;      // 核心處理位點 + ID 計數器
    public static final byte PK_GW_INBOUND_SEQ = 2;    // 網關發送位點 (Stream 10)
    public static final byte PK_GW_OUTBOUND_SEQ = 3;   // 網關接收位點 (Stream 30)
    public static final byte PK_PUB_PUSH_SEQ = 4;      // WebSocket 推送位點
    public static final byte PK_CORE_OUTBOUND_SEQ = 5; // 核心回報發送位點 (Stream 30)

    // --- Aeron 頻道 ---
    public static final String INBOUND_CHANNEL = "aeron:udp?endpoint=localhost:40444";
    public static final String OUTBOUND_CHANNEL = "aeron:udp?endpoint=localhost:40445";
    public static final int INBOUND_STREAM_ID = 10;
    public static final int OUTBOUND_STREAM_ID = 30;

    public static final int ASSET_BTC = 1;
    public static final int ASSET_USDT = 2;
}
