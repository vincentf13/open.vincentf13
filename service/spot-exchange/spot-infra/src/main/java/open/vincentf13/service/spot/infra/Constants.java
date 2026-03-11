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
    public static class MetaDataKey {
        public static final byte PK_CORE_ENGINE = 1;
        public static final byte PK_GW_COMMAND_SENDER = 2;
        public static final byte PK_GW_RESULT_RECEIVER = 3;
        public static final byte PK_GW_WS_PUSH_WORKER = 4;
        public static final byte PK_CORE_RESULT_SENDER = 5;
        public static final byte PK_CORE_COMMAND_RECEIVER = 6;
    }

    /** 
      系統指令類型 (MsgType)
     */
    public static class MsgType {
        public static final int ORDER_CREATE = 100;
        public static final int DEPOSIT = 102;
        public static final int AUTH = 103;
    }

    /** 
      資產 ID (Asset)
     */
    public static class Asset {
        public static final int BTC = 1;
        public static final int USDT = 2;
    }

    /**
      Chronicle Map 檔案與名稱列舉
     */
    public static class ChronicleMapEnum {
        public static final String DEFAULT_BASE_DIR = "data/spot-exchange/";
        public static final String BALANCES = "balances";
        public static final String USER_ASSETS = "user-assets";
        public static final String ORDERS = "orders";
        public static final String ACTIVE_ORDERS = "active-idx";
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
        GATEWAY("gw-queue"),
        COMMAND("core-queue"),
        RESULT("outbound-queue");

        private final String path;
    }

    /**
      Chronicle Wire 數據欄位定義
     */
    public enum ChronicleWireKey implements WireKey {
        msgType, payload, aeronSeq, userId, topic, data, timestamp
    }

    /**
      Aeron 頻道與串流配置
     */
    public static class AeronChannel {
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
