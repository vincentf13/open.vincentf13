package open.vincentf13.service.spot.infra;

public class Constants {
    public static final long SCALE = 100_000_000L;
    public static final long SYSTEM_USER_ID = 0L;
    public static final long ID_REJECTED = 0L;

    public static final byte PK_CORE_PROGRESS = 1;
    public static final byte PK_GW_INBOUND_SEQ = 2;
    public static final byte PK_GW_OUTBOUND_SEQ = 3;
    public static final byte PK_PUB_PUSH_SEQ = 4;
    public static final byte PK_CORE_OUTBOUND_SEQ = 5;

    public static final String INBOUND_CHANNEL = "aeron:udp?endpoint=localhost:40444";
    public static final String OUTBOUND_CHANNEL = "aeron:udp?endpoint=localhost:40445";
    public static final int INBOUND_STREAM_ID = 10;
    public static final int OUTBOUND_STREAM_ID = 30;

    public static final int ASSET_BTC = 1;
    public static final int ASSET_USDT = 2;

    public static final int MSG_TYPE_ORDER_CREATE = 100;
    public static final int MSG_TYPE_ORDER_CANCEL = 101;
    public static final int MSG_TYPE_DEPOSIT = 102;
    public static final int MSG_TYPE_AUTH = 103;
}
