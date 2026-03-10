package open.vincentf13.service.spot.infra;

import net.openhft.chronicle.wire.WireKey;

/**
  全系統共享核心常數 (整合版)
  採用內部類別區分職責，提供清晰的命名空間，避免常數名過長
 */
public class Constants {
    /** 系統財務精度縮放倍數 (10^8) */
    public static final long SCALE = 100_000_000L;
    
    /** 
      平台系統用戶 ID
      用於歸集成交捨入差額 (Dust) 及手續費收取的專用虛擬帳戶
     */
    public static final long PLATFORM_USER_ID = 0L;
    
    /** 
      無效或拒絕標識 ID
      在冪等性校驗 Map 中，若 ClientOrderId 對應此 ID，表示該請求已被拒絕
     */
    public static final long ID_REJECTED = 0L;

    /**
      進度位點 Key (Progress Keys)
      用於在 Metadata Map 中標識各組件的持久化處理位置
     */
    public static class Pk {
        /** 核心撮合引擎處理進度 */
        public static final byte ENGINE = 1;
        /** 網關指令發送至 Aeron 的進度 */
        public static final byte GATEWAY_IN = 2;
        /** 網關從 Aeron 接收回報的進度 */
        public static final byte GATEWAY_OUT = 3;
        /** WebSocket 推播服務處理進度 */
        public static final byte PUSH = 4;
        /** 撮合核心回報發送至 Aeron 的進度 */
        public static final byte RESULT = 5;
    }

    /**
      Chronicle Wire 數據欄位定義 (建模版)
      提供編譯期檢查與極致的序列化尋址效能
     */
    public enum Fields implements WireKey {
        msgType,    // 訊息類型
        payload,    // 二進制載體
        aeronSeq,   // Aeron 傳輸序號
        userId,     // 用戶 ID
        topic,      // 業務主題
        data,       // JSON 數據體
        timestamp   // 時間戳
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
      指令類型 (Message Types)
     */
    public static class Msg {
        public static final int ORDER_CREATE = 100;
        public static final int ORDER_CANCEL = 101;
        public static final int DEPOSIT = 102;
        public static final int AUTH = 103;
    }

    /**
      資產 ID
     */
    public static class Asset {
        public static final int BTC = 1;
        public static final int USDT = 2;
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
