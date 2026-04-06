package open.vincentf13.service.spot.matching.engine;

import net.openhft.chronicle.map.ChronicleMap;
import org.agrona.collections.Long2LongHashMap;
import open.vincentf13.service.spot.infra.chronicle.LongValue;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.model.CidKey;

/**
 * 冪等性守衛 (Idempotency Guard)
 *
 * 職責：透過記憶體環形緩衝 + 磁碟 CID Map 兩級去重。
 *
 * 雙緩衝非同步落盤：
 * - matching thread 寫入 active 緩衝；當 active 滿時呼叫 rotate 翻轉指針
 * - flusher thread 透過 drainToDisk 將 draining 緩衝寫入 ChronicleMap
 * - isDuplicate 同時檢查兩個緩衝 + 磁碟，保證在途訂單不被誤判為重複
 */
public class IdempotencyGuard implements DiskSink {
    private static final int BUFFER_SIZE = 65536;
    private static final long MISSING = Long.MIN_VALUE;

    private static final class Buffer {
        final long[] uids = new long[BUFFER_SIZE];
        final long[] cids = new long[BUFFER_SIZE];
        final long[] oids = new long[BUFFER_SIZE];
        /** O(1) 查詢：key = userId ^ clientOrderId，value = orderId。取代 O(n) 線性掃描 */
        final Long2LongHashMap index = new Long2LongHashMap(BUFFER_SIZE, 0.6f, MISSING);
        int count = 0;
    }

    private final Buffer bufA = new Buffer();
    private final Buffer bufB = new Buffer();
    private Buffer active = bufA;              // 只由 matching thread 寫入
    private volatile Buffer draining = null;   // 由 matching 寫入，flusher 讀後 null

    private final ChronicleMap<CidKey, LongValue> diskMap = Storage.self().clientOrderIdMap();

    // Bloom filter: 覆蓋所有已 flush 到 disk 的 clientOrderId。
    // "definitely not in disk" → 跳過 mmap 讀取（省 1-2μs）
    // "maybe in disk" → 仍查 disk（正確性保障）
    private static final int BLOOM_BITS = 1 << 22; // 4M bits = 512KB
    private static final int BLOOM_MASK = BLOOM_BITS - 1;
    private final long[] bloom = new long[BLOOM_BITS >>> 6]; // 4M / 64 = 65536 longs

    // matching thread 專用 key/value（單執行緒，不競爭）
    private final CidKey matchingKey = new CidKey();

    // flusher thread 專用 key/value
    private final CidKey flusherKey = new CidKey();
    private final LongValue flusherValue = new LongValue();

    private static long cidKey(long userId, long clientOrderId) { return userId ^ (clientOrderId * 0x9E3779B97F4A7C15L); }

    private void bloomAdd(long hash) {
        bloom[(int) ((hash >>> 0) & BLOOM_MASK) >>> 6] |= 1L << (hash & 63);
        bloom[(int) ((hash >>> 16) & BLOOM_MASK) >>> 6] |= 1L << ((hash >>> 16) & 63);
        bloom[(int) ((hash >>> 32) & BLOOM_MASK) >>> 6] |= 1L << ((hash >>> 32) & 63);
    }

    private boolean bloomMayContain(long hash) {
        return (bloom[(int) ((hash >>> 0) & BLOOM_MASK) >>> 6] & (1L << (hash & 63))) != 0
            && (bloom[(int) ((hash >>> 16) & BLOOM_MASK) >>> 6] & (1L << ((hash >>> 16) & 63))) != 0
            && (bloom[(int) ((hash >>> 32) & BLOOM_MASK) >>> 6] & (1L << ((hash >>> 32) & 63))) != 0;
    }

    /** 檢查是否為重複指令：O(1) HashMap + Bloom filter 前置，避免 mmap 讀取 */
    public boolean isDuplicate(long userId, long clientOrderId) {
        long key = cidKey(userId, clientOrderId);
        // 1. O(1) 查 active buffer
        if (active.index.get(key) != MISSING) return true;
        // 2. O(1) 查 draining buffer
        Buffer d = draining;
        if (d != null && d.index.get(key) != MISSING) return true;
        // 3. Bloom filter：若 "definitely not"，跳過 disk（省 1-2μs mmap）
        if (!bloomMayContain(key)) return false;
        // 4. Bloom filter 說 "maybe"，查 disk 確認
        matchingKey.set(userId, clientOrderId);
        return diskMap.containsKey(matchingKey);
    }

    /** 記錄新的冪等映射（matching thread 呼叫） */
    public void record(long userId, long clientOrderId, long orderId) {
        if (active.count >= BUFFER_SIZE) {
            forceRotateOrInlineFlush();
        }
        active.uids[active.count] = userId;
        active.cids[active.count] = clientOrderId;
        active.oids[active.count] = orderId;
        active.index.put(cidKey(userId, clientOrderId), orderId);
        active.count++;
    }

    /** matching thread 呼叫：若 draining 已清空，將 active 翻轉為 draining */
    @Override
    public boolean rotate() {
        if (draining != null) return false;     // flusher 尚未完成上一輪
        if (active.count == 0) return false;    // 無待寫資料
        Buffer wasActive = active;
        active = (active == bufA) ? bufB : bufA;
        // 切換後的 active 必為空（flusher 已 clear）或首次使用
        draining = wasActive;                    // volatile write，發布給 flusher
        return true;
    }

    /** flusher thread 呼叫：將 draining 緩衝寫入 ChronicleMap，完成後釋放 */
    @Override
    public void drainToDisk() {
        Buffer d = draining;
        if (d == null) return;
        for (int i = 0; i < d.count; i++) {
            flusherKey.set(d.uids[i], d.cids[i]);
            flusherValue.set(d.oids[i]);
            diskMap.put(flusherKey, flusherValue);
            // 加入 Bloom filter，讓後續 isDuplicate 能跳過 disk check
            bloomAdd(cidKey(d.uids[i], d.cids[i]));
        }
        d.count = 0;
        d.index.clear();
        draining = null;                         // volatile write，釋放給 matching
    }

    /** 冷啟動清空（recovery 路徑呼叫） */
    public void clearDisk() {
        diskMap.clear();
        active.count = 0;
        active.index.clear();
        Buffer d = draining;
        if (d != null) { d.count = 0; d.index.clear(); }
        draining = null;
    }

    /** 同步 flush 所有 active 緩衝內容至磁碟（僅 recovery 冷啟動階段使用，單執行緒環境） */
    public void flushInlineForRecovery() {
        if (active.count == 0) return;
        // 使用獨立 key/value 避免與 flusher thread 共用 reusable 物件
        CidKey recoveryKey = new CidKey();
        LongValue recoveryValue = new LongValue();
        for (int i = 0; i < active.count; i++) {
            recoveryKey.set(active.uids[i], active.cids[i]);
            recoveryValue.set(active.oids[i]);
            diskMap.put(recoveryKey, recoveryValue);
        }
        active.count = 0;
    }

    /** 緊急情況：active 寫滿但 flusher 尚未處理。spin 等 flusher 完成；極端超時才 fallback */
    private void forceRotateOrInlineFlush() {
        // 長時間 spin 等待 flusher 完成（20ms 內 flusher 至少跑一輪；10000 iterations ~= ms 級）
        for (int spin = 0; spin < 10000; spin++) {
            if (draining == null) {
                Buffer wasActive = active;
                active = (active == bufA) ? bufB : bufA;
                draining = wasActive;
                return;
            }
            Thread.onSpinWait();
        }
        // 極端 fallback：flusher 卡死才走到這裡。使用獨立 key 避免與 flusher 共用狀態
        CidKey fallbackKey = new CidKey();
        LongValue fallbackValue = new LongValue();
        for (int i = 0; i < active.count; i++) {
            fallbackKey.set(active.uids[i], active.cids[i]);
            fallbackValue.set(active.oids[i]);
            diskMap.put(fallbackKey, fallbackValue);
        }
        active.count = 0;
    }
}
