package open.vincentf13.service.spot.infra;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.openhft.chronicle.wire.WireKey;

/**
  全系統共享核心常數
 */
public class Constants {
    /** 系統財務精度縮放倍數 (10^8) */
    public static final long SCALE = 100_000_000L;
    /** 平台系統用戶 ID */
    public static final long PLATFORM_USER_ID = 0L;
    /** 無效或拒絕標識 ID */
    public static final long ID_REJECTED = 0L;

    /** 
      進度位點元數據 Key (MetaDataKey)
     */
    public static final String[] ORDER_STATUS_STRINGS = {"NEW", "PARTIALLY_FILLED", "FILLED", "REJECTED", "CANCELED"};

    public static class OrderSide {
        public static final byte BUY = 0;
        public static final byte SELL = 1;
    }

    public static class MatchingConfig {
        public static final int INITIAL_BOOK_ORDER_COUNT = 200_000;
        public static final int INITIAL_BOOK_LEVEL_CAPACITY = 4096;
        public static final int INITIAL_POOL_SIZE = 100_000;
        public static final int STARTUP_PRE_ALLOCATE_COUNT = 1000;
    }

    public static class MetaDataKey {
        public static final byte MACHING_RECEVIER_POINT = 1;
        public static final byte MACHING_ENGINE_POINT = 2;
        public static final byte GW_RECEVIER_POINT = 3;
        public static final byte WS_PUSH_TO_CLIENT_POINT = 4;
        public static final byte LAST_SNAPSHOT_INFO = 5; // 新增：最後一次成功快照的元數據
    }

    /** 
      系統指令類型 (MsgType)
     */
    public static class MsgType {
        public static final int ORDER_CREATE = 100;
        public static final int ORDER_CANCEL = 101;
        public static final int DEPOSIT = 102;
        public static final int AUTH = 103;
        public static final int EXECUTION_REPORT = 104;
        public static final int AUTH_REPORT = 105; // 新增：認證結果回報
        public static final int SNAPSHOT = 106; // 新增：快照指令 (內部觸發)
        public static final int RESUME = 200;
    }

    /** 
      資產 ID (Asset)
     */
    public static class Asset {
        public static final int BTC = 1;
        public static final int USDT = 2;
    }

    /** 
      Aeron 通訊組件工作狀態 (AeronState)
     */
    public enum AeronState {
        WAITING, // 等待握手/同步中
        SENDING  // 正常發送數據中
    }

    /**
      Chronicle Map 檔案與名稱列舉
     */
    public static class ChronicleMapEnum {
        // 從系統環境變數獲取路徑，預設為 "data/spot-exchange/"
        public static final String DEFAULT_BASE_DIR = System.getProperty("SPOT_MAP_DIR", "data/spot-exchange/map/");
        public static final String WAL_BASE_DIR = System.getProperty("SPOT_WAL_DIR", "data/spot-exchange/wal/");
        public static final String SNAPSHOT_BASE_DIR = System.getProperty("SPOT_SNAPSHOT_DIR", "data/spot-exchange/snapshot/");
        
        public static final String BALANCES = "balances";
        public static final String USER_ASSETS = "user-assets";
        public static final String ORDERS = "orders";
        public static final String ACTIVE_ORDERS = "active-idx";
        // 新增二級索引：用戶的活躍訂單 (用於 Query)
        public static final String USER_ACTIVE_ORDERS = "user-active-orders";
        public static final String TRADES = "trades";
        public static final String CIDS = "cid-idx";
        public static final String METADATA = "metadata";
    }

    /** 
      Chronicle Queue 檔案名稱列舉
     */
    @Getter
    @RequiredArgsConstructor
    public enum ChronicleQueueEnum {
        CLIENT_TO_GW("client-to-gw-wal"),
        GW_TO_MATCHING("gw-to-matching-wal"),
        MATCHING_TO_GW("matching-to-gw-wal");

        private final String path;
    }

    /**
      Chronicle Wire 數據欄位定義
     */
    public enum ChronicleWireKey implements WireKey {
        msgType, payload, gwSeq, matchingSeq, userId, assetId, data, timestamp
    }

    /**
      Aeron 頻道與串流配置
     */
    public static class AeronChannel {
        /** 發送至 Matching Core 的通道 */
        public static final String MATCHING_URL = System.getProperty("AERON_MATCHING_URL", "aeron:udp?endpoint=localhost:40444");
        /** 發送至 Gateway 的通道 */
        public static final String GATEWAY_URL = System.getProperty("AERON_GATEWAY_URL", "aeron:udp?endpoint=localhost:40445");
        
        /** 統一數據流 ID */
        public static final int DATA_STREAM_ID = 10;
        /** 統一控制流 ID (用於反向握手) */
        public static final int CONTROL_STREAM_ID = 11;
    }

    /**
      WebSocket 專用常數
     */
    public static class Ws {
        public static final String PATH = "/ws/spot";
        public static final String PONG = "{\"op\":\"pong\"}";
        public static final String CREATE = "order.create";
        public static final String CANCEL = "order.cancel";
        public static final String DEPOSIT = "deposit";
        public static final String AUTH = "auth";
        public static final String PING = "ping";
        public static final String OP = "op";
        public static final String CID = "cid";
        public static final String PARAMS = "params";
        public static final String ARGS = "args";
        public static final String USER_ID = "userId";
        public static final String ORDER_ID = "orderId";
        public static final String SYMBOL_ID = "symbolId";
        public static final String PRICE = "price";
        public static final String QTY = "qty";
        public static final String SIDE = "side";
        public static final String ASSET_ID = "assetId";
        public static final String AMOUNT = "amount";
    }
}
