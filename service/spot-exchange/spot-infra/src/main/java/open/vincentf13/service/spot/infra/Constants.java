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
        public static final byte MACHING_RECEVIER_POINT = 1;
        public static final byte MACHING_ENGINE_POINT = 2;
        public static final byte GW_RECEVIER_POINT = 3;
        public static final byte WS_PUSH_TO_CLIENT_POINT = 4;

    }

    /** 
      系統指令類型 (MsgType)
     */
    public static class MsgType {
        public static final int ORDER_CREATE = 100;
        public static final int DEPOSIT = 102;
        public static final int AUTH = 103;
        public static final int RESUME = 200; // 災難恢復握手訊號
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
        CLIENT_TO_GW("client-to-gw-wal"),
        GW_TO_MATCHING("gw-to-matching-wal"),
        MATCHING_TO_GW("matching-to-gw-wal");

        private final String path;
    }

    /**
      Chronicle Wire 數據欄位定義
     */
    public enum ChronicleWireKey implements WireKey {
        msgType, payload, gwSeq, matchingSeq, userId, topic, data, timestamp
    }

    /**
      Aeron 頻道與串流配置
     */
    public static class AeronChannel {
        /** 發送至 Matching Core 的通道 */
        public static final String MATCHING_URL = "aeron:udp?endpoint=localhost:40444";
        /** 發送至 Gateway 的通道 */
        public static final String GATEWAY_URL = "aeron:udp?endpoint=localhost:40445";
        
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
