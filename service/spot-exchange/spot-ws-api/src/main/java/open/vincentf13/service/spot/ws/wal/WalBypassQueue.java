package open.vincentf13.service.spot.ws.wal;

/**
 * WAL Bypass Queue — 繞過 Chronicle Queue 的 lock-free 單生產者單消費者 ring buffer。
 *
 * 啟用 -Dspot.wal.bypass=true 時，WalWriter 直接推送到此 queue，
 * AeronSender 從此 queue 讀取而非 Chronicle Queue tailer。
 * 用於驗證 Chronicle Queue mmap 是否為 transport P99 spike 的根因。
 *
 * 全預分配，zero-allocation。Single producer (WalWriter), single consumer (AeronSender)。
 */
public class WalBypassQueue {

    public static final boolean ENABLED = Boolean.getBoolean("spot.wal.bypass");

    private static final int CAPACITY = 1 << 16; // 65536
    private static final int MASK = CAPACITY - 1;
    private static final Slot[] RING = new Slot[CAPACITY];

    private static volatile long writePos = 0;
    private static long readPos = 0; // only AeronSender reads

    static {
        for (int i = 0; i < CAPACITY; i++) RING[i] = new Slot();
    }

    /** 預分配 slot，所有欄位為各業務類型的 union */
    public static class Slot {
        public int msgType;
        public long arrivalTimeNs, timestamp, userId;
        public int symbolId;
        public long price, qty, clientOrderId, orderId, amount;
        public byte side;
        public int assetId;
        public volatile boolean ready; // 標記 slot 已寫完，消費者可讀

        public void copyFrom(WalEvent e) {
            this.msgType = e.msgType;
            this.arrivalTimeNs = e.arrivalTimeNs;
            this.timestamp = e.timestamp;
            this.userId = e.userId;
            switch (e.msgType) {
                case open.vincentf13.service.spot.infra.Constants.MsgType.ORDER_CREATE -> {
                    WalOrderCreate oc = e.orderCreate;
                    this.symbolId = oc.symbolId;
                    this.price = oc.price;
                    this.qty = oc.qty;
                    this.side = oc.side;
                    this.clientOrderId = oc.clientOrderId;
                }
                case open.vincentf13.service.spot.infra.Constants.MsgType.ORDER_CANCEL -> {
                    this.orderId = e.orderCancel.orderId;
                }
                case open.vincentf13.service.spot.infra.Constants.MsgType.DEPOSIT -> {
                    this.assetId = e.deposit.assetId;
                    this.amount = e.deposit.amount;
                }
            }
        }
    }

    /** WalWriter 呼叫：將 event 推入 ring buffer */
    public static void offer(WalEvent e) {
        long wp = writePos;
        Slot slot = RING[(int) (wp & MASK)];
        slot.copyFrom(e);
        slot.ready = true;
        writePos = wp + 1; // volatile write, 確保消費者可見
    }

    /** AeronSender 呼叫：取出一個 slot，返回 null 表示沒有數據 */
    public static Slot poll() {
        if (readPos >= writePos) return null;
        Slot slot = RING[(int) (readPos & MASK)];
        if (!slot.ready) return null;
        slot.ready = false;
        readPos++;
        return slot;
    }
}
