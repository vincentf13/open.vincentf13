package open.vincentf13.service.spot.infra.chronicle;

import net.openhft.chronicle.wire.WireKey;

/**
 * WAL BinaryWire 欄位名稱常數 (WireKey)
 *
 * 供 WalWriter 寫入與 AeronSender 讀取時使用，
 * 確保讀寫雙方欄位名稱一致，並透過 WireKey ordinal 加速 BinaryWire 查找。
 */
public enum WalField implements WireKey {
    // ---- 共用欄位 (所有訊息類型皆有) ----
    msgType,
    gwTime,
    userId,
    timestamp,

    // ---- OrderCreate ----
    symbolId,
    price,
    qty,
    side,
    clientOrderId,

    // ---- OrderCancel ----
    orderId,

    // ---- Deposit ----
    assetId,
    amount
}
