package open.vincentf13.service.spot.infra;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

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
    /** Chronicle Queue 的無效/初始索引 */
    public static final long WAL_INDEX_NONE = -1L;
    /** Aeron 消息流的無效/初始序號 */
    public static final long MSG_SEQ_NONE = -1L;

    /** 
      進度位點元數據 Key (MetaDataKey)
     */
    public static class OrderSide {
        public static final byte BUY = 0;
        public static final byte SELL = 1;
    }

    public static class MatchingConfig {
        public static final int INITIAL_BOOK_ORDER_COUNT = 200_000;
        public static final int INITIAL_BOOK_LEVEL_CAPACITY = 4096;
        public static final int STARTUP_PRE_ALLOCATE_COUNT = 1000;
    }

    public static class MetaDataKey {
        /** 網路消息進度 (MsgProgress) 的 Key */
        public static class MsgProgress {
            public static final byte MATCHING_ENGINE_RECEIVE = 1;
            public static final byte GATEWAY_RECEIVE = 2;
            public static final byte GATEWAY_SENDER_POINT = 3;
            public static final byte MATCHING_SENDER_POINT = 4;
        }

        /** WAL 索引與狀態進度 (WalProgress) 的 Key */
        public static class Wal {
            public static final byte MATCHING_ENGINE_POINT = 1;
            public static final byte WS_PUSH_TO_CLIENT_POINT = 2;
        }
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
        public static final int ORDER_ACCEPTED = 107;
        public static final int ORDER_REJECTED = 108;
        public static final int ORDER_CANCELED = 109;
        public static final int ORDER_MATCHED = 110;
        public static final int DEPOSIT_REPORT = 111;
        public static final int RESUME = 200;
    }

    /** 
      資產 ID (Asset)
     */
    @Getter
    @RequiredArgsConstructor
    public enum Asset {
        BTC(1),
        USDT(2);
        
        private final int id;
        
        public static Asset of(int id) {
            for (Asset a : values()) {
                if (a.id == id) return a;
            }
            return null;
        }
    }

    /** 
      交易對 ID (Symbol) 與其對應資產
     */
    @Getter
    @RequiredArgsConstructor
    public enum Symbol {
        BTCUSDT(1001, Asset.BTC.getId(), Asset.USDT.getId());
        
        private final int id;
        private final int baseAssetId;
        private final int quoteAssetId;
        
        public static Symbol of(int id) {
            for (Symbol s : values()) {
                if (s.id == id) return s;
            }
            return null;
        }
    }

    public static class Matching {
        public static final int ENGINE_BATCH_SIZE = 500;
    }

    /**
     * 監控指標 Key 定義 (Metrics Keys)
     */
    public static class MetricsKey {
        public static final long NETTY_RECV_COUNT = 3L;
        public static final long AERON_BACKPRESSURE = 4L;
        public static final long GATEWAY_WAL_WRITE_COUNT = 5L;
        public static final long AERON_SEND_COUNT = 6L;
        public static final long AERON_RECV_COUNT = 15L;
        public static final long AERON_DROPPED_COUNT = 7L; // 新增：丟包計數指標
        public static final long GATEWAY_WAL_DROP_COUNT = 8L; // 新增：網關 WAL 佇列丟失計數
        // 業務指標 (Counters)
        public static final long MATCH_COUNT = 9L;
        public static final long ORDER_ACCEPTED_COUNT = 12L;
        public static final long ORDER_REJECTED_COUNT = 13L;
        public static final long ORDER_PROCESSED_COUNT = 14L;

        // 延遲分佈指標 (Latencies - Nanoseconds)
        public static final long LATENCY_MATCHING = 10L;
        public static final long LATENCY_TRANSPORT = 11L;

        // 分位數指標 (Percentiles) - 這些用於 Chronicle Map 編碼，不直接作為 Micrometer Key
        public static final long P50 = 50L;
        public static final long P90 = 90L;
        public static final long P99 = 99L;
        public static final long P999 = 999L;
        public static final long MAX = 100L;

        // CPU ID 監測指標 (執行緒綁核追蹤) - 使用 1000+ 避開百分位數
        public static final long CPU_ID_WAL_WRITER = 1001L;
        public static final long CPU_ID_AERON_SENDER = 1002L;
        public static final long CPU_ID_AERON_RECEIVER = 1003L;
        public static final long CPU_ID_JVM_MANAGEMENT = 1005L;
        public static final long CPU_ID_NETTY_BOSS = 1006L;
        public static final long CPU_ID_NETTY_WORKER_1 = 1007L;
        public static final long CPU_ID_NETTY_WORKER_2 = 1008L;
        public static final long CPU_ID_NETTY_WORKER_3 = 1010L;
        public static final long CPU_ID_NETTY_WORKER_4 = 1011L;
        public static final long CPU_ID_AERON_CONDUCTOR = 1009L;
        public static final long CPU_ID_CURRENT_AERON_SENDER = 1012L;
        public static final long CPU_ID_CURRENT_AERON_RECEIVER = 1013L;
        public static final long CPU_ID_CURRENT_NETTY_BOSS = 1014L;
        public static final long CPU_ID_CURRENT_NETTY_WORKER_1 = 1015L;
        public static final long CPU_ID_CURRENT_NETTY_WORKER_2 = 1016L;
        public static final long CPU_ID_CURRENT_NETTY_WORKER_3 = 1017L;
        public static final long CPU_ID_CURRENT_WAL_WRITER = 1019L;
        public static final long CPU_ID_CURRENT_NETTY_WORKER_4 = 1018L;

        // JVM 與 CPU 指標
        public static final long MATCHING_JVM_USED_MB = 200L;
        public static final long MATCHING_JVM_MAX_MB = 201L;
        public static final long MATCHING_AERON_RECEVIER_WORKER_DUTY_CYCLE = 202L;
        
        public static final long GATEWAY_JVM_USED_MB = 210L;
        public static final long GATEWAY_JVM_MAX_MB = 211L;
        public static final long GATEWAY_AERON_SENDER_WORKER_DUTY_CYCLE = 212L;
        public static final long GATEWAY_WAL_WRITER_DUTY_CYCLE = 213L;

        // GC 監控指標
        public static final long MATCHING_GC_COUNT = 300L;
        public static final long MATCHING_GC_LAST_INTERVAL_MS = 301L;
        public static final long MATCHING_GC_LAST_DURATION_MS = 302L;
        
        public static final long GATEWAY_GC_COUNT = 310L;
        public static final long GATEWAY_GC_LAST_INTERVAL_MS = 311L;
        public static final long GATEWAY_GC_LAST_DURATION_MS = 312L;

        // GC 歷史記錄 (最近 100 筆時間戳)
        public static final long MATCHING_GC_HISTORY_START = 400L;
        public static final long GATEWAY_GC_HISTORY_START = 500L;
        public static final int GC_HISTORY_MAX_KEEP = 100;
    }

    /**
      Chronicle Map 檔案與名稱列舉
     */
    public static class ChronicleMapEnum {
        public static final String DEFAULT_BASE_DIR = System.getProperty("SPOT_MAP_DIR", "C:/iProject/open.vincentf13/data/spot-exchange/map/");
        public static final String WAL_BASE_DIR = System.getProperty("SPOT_WAL_DIR", "C:/iProject/open.vincentf13/data/spot-exchange/wal/");
        
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
        CLIENT_TO_GW("client-to-gw-wal");

        private final String path;
    }

    /**
      Aeron 頻道與串流配置
     */
    public static class AeronChannel {
        /** 指令流 (Gateway -> Matching) 使用 IPC，加大 Term Buffer 至 256MB */
        public static final String MATCHING_FLOW = "aeron:ipc?term-length=256M";
        
        /** 回報流 (Matching -> Gateway) 使用 IPC，加大 Term Buffer 至 256MB */
        public static final String REPORT_FLOW = "aeron:ipc?term-length=256M";
        
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
