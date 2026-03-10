package open.vincentf13.service.spot.infra;

public class Constants {
    public static final long SCALE = 100_000_000L;
    public static final long SYSTEM_USER_ID = 0L;
    public static final long ID_REJECTED = 0L;
    
    // --- 元數據 Key ---
    public static final byte PK_CORE_PROGRESS = 1;
    public static final byte PK_GW_INBOUND_SEQ = 2;
    public static final byte PK_GW_OUTBOUND_SEQ = 3;
    public static final byte PK_PUB_PUSH_SEQ = 4;
    public static final byte PK_CORE_OUTBOUND_SEQ = 5;
    
    // --- Aeron 頻道 ---
    public static final String INBOUND_CHANNEL = "aeron:udp?endpoint=localhost:40444";
    public static final String OUTBOUND_CHANNEL = "aeron:udp?endpoint=localhost:40445";
    public static final int INBOUND_STREAM_ID = 10;
    public static final int OUTBOUND_STREAM_ID = 30;
    
    // --- 訊息類型 ID ---
    public static final int MSG_TYPE_ORDER_CREATE = 100;
    public static final int MSG_TYPE_ORDER_CANCEL = 101;
    public static final int MSG_TYPE_DEPOSIT = 102;
    public static final int MSG_TYPE_AUTH = 103;
    
    // --- WebSocket 操作名稱 (OP) ---
    public static final String OP_ORDER_CREATE = "order.create";
    public static final String OP_AUTH = "auth";
    public static final String OP_PING = "ping";
    public static final String RESP_PONG = "{\"op\":\"pong\"}";
    
    // --- JSON 欄位名稱 ---
    public static final String FIELD_OP = "op";
    public static final String FIELD_CID = "cid";
    public static final String FIELD_PARAMS = "params";
    public static final String FIELD_ARGS = "args";
    public static final String FIELD_USER_ID = "userId";
    public static final String FIELD_SYMBOL_ID = "symbolId";
    public static final String FIELD_PRICE = "price";
    public static final String FIELD_QTY = "qty";
    public static final String FIELD_SIDE = "side";
    
    // --- 資產 ID ---
    public static final int ASSET_BTC = 1;
    public static final int ASSET_USDT = 2;
}
