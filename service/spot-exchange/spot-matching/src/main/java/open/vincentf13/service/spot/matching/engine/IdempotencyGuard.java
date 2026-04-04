package open.vincentf13.service.spot.matching.engine;

import net.openhft.chronicle.map.ChronicleMap;
import open.vincentf13.service.spot.infra.chronicle.LongValue;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.model.CidKey;

/**
 * 冪等性守衛 (Idempotency Guard)
 *
 * 職責：透過記憶體環形緩衝 + 磁碟 CID Map 兩級去重，
 * 防止同一 (userId, clientOrderId) 重複下單。
 * 單線程存取（matching thread），Zero-GC 設計。
 */
public class IdempotencyGuard {
    private static final int BUFFER_SIZE = 4096;
    private final long[] pendingUids = new long[BUFFER_SIZE];
    private final long[] pendingCids = new long[BUFFER_SIZE];
    private final long[] pendingOids = new long[BUFFER_SIZE];
    private int count = 0;

    private final ChronicleMap<CidKey, LongValue> diskMap = Storage.self().clientOrderIdMap();
    private final CidKey reusableKey = new CidKey();
    private final LongValue reusableValue = new LongValue();

    /** 檢查是否為重複指令 (先查緩衝，再查磁碟) */
    public boolean isDuplicate(long userId, long clientOrderId) {
        for (int i = 0; i < count; i++) {
            if (pendingUids[i] == userId && pendingCids[i] == clientOrderId) return true;
        }
        reusableKey.set(userId, clientOrderId);
        return diskMap.containsKey(reusableKey);
    }

    /** 記錄新的冪等映射 (userId, clientOrderId) → orderId */
    public void record(long userId, long clientOrderId, long orderId) {
        if (count >= BUFFER_SIZE) flush();
        pendingUids[count] = userId;
        pendingCids[count] = clientOrderId;
        pendingOids[count] = orderId;
        count++;
    }

    /** 批量寫入磁碟 */
    public void flush() {
        for (int i = 0; i < count; i++) {
            reusableKey.set(pendingUids[i], pendingCids[i]);
            reusableValue.set(pendingOids[i]);
            diskMap.put(reusableKey, reusableValue);
        }
        count = 0;
    }

    /** 冷啟動清空 */
    public void clearDisk() {
        diskMap.clear();
        count = 0;
    }
}
