package open.vincentf13.service.spot.ws.wal;

/**
 * Disruptor RingBuffer 預分配事件 (Pre-allocated Event)
 *
 * 每個 slot 包含所有業務類型的 model 實例 (Union)，
 * 根據 msgType 決定哪個 model 為有效數據。全部預分配，Zero-GC。
 */
public class WalEvent {
    // ---- 共用欄位 (所有訊息類型皆有) ----
    public int msgType;
    public long arrivalTimeNs;
    public long timestamp;
    public long userId;

    // ---- 各業務類型專屬欄位 (根據 msgType 選用) ----
    public final WalOrderCreate orderCreate = new WalOrderCreate();
    public final WalOrderCancel orderCancel = new WalOrderCancel();
    public final WalDeposit deposit = new WalDeposit();
    // AUTH: 僅需 userId，無額外欄位
}
