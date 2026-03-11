package open.vincentf13.service.spot.infra;

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

    // --- 進度位點 Key (PK) ---
    public static final byte PK_ENGINE = 1;
    public static final byte PK_GATEWAY_IN = 2;
    public static final byte PK_GATEWAY_OUT = 3;
    public static final byte PK_PUSH = 4;
    public static final byte PK_RESULT = 5;

    // --- 指令類型 (MSG) ---
    public static final int MSG_ORDER_CREATE = 100;
    public static final int MSG_DEPOSIT = 102;
    public static final int MSG_AUTH = 103;

    // --- 資產 ID (ASSET) ---
    public static final int ASSET_BTC = 1;
    public static final int ASSET_USDT = 2;

    /**
      存儲相關配置與檔案名稱 (Store)
     */
    public static class Store {
        public static final String DEFAULT_BASE_DIR = "data/spot-exchange/";
        public static final String BALANCES = "balances";
        public static final String USER_ASSETS = "user-assets";
        public static final String ORDERS = "orders";
        public static final String ACTIVE_ORDERS = "active-idx";
        public static final String TRADES = "trades";
        public static final String CIDS = "cid-idx";
        public static final String METADATA = "metadata";
        public static final String Q_GATEWAY = "gw-queue";
        public static final String Q_COMMAND = "core-queue";
        public static final String Q_RESULT = "outbound-queue";
    }

    /**
      Chronicle Wire 數據欄位定義
     */
    public enum Fields implements WireKey {
        msgType, payload, aeronSeq, userId, topic, data, timestamp
    }

    /**
      Aeron 頻道與串流配置
     */
    public static class Channel {
        public static final String INBOUND = "aeron:udp?endpoint=localhost:40444";
        public static final String OUTBOUND = "aeron:udp?endpoint=localhost:40445";
        public static final int IN_STREAM = 10;
        public static final int OUT_STREAM = 30;
    }

    /**
      WebSocket 專用常數
     */
    public static class Ws {
        public static final String PATH = "/ws/spot";
        public static final String PONG = "{\"op\":\"pong\"}";
        public static final String CREATE = "order.create";
        public static final String AUTH = "auth";
        public static final String PING = "ping";
        public static final String OP = "op";
        public static final String CID = "cid";
        public static final String PARAMS = "params";
        public static final String ARGS = "args";
        public static final String USER_ID = "userId";
        public static final String SYMBOL_ID = "symbolId";
        public static final String PRICE = "price";
        public static final String QTY = "qty";
        public static final String SIDE = "side";
    }
}
