package open.vincentf13.service.spot.matching.engine;

/**
 * 非同步磁碟落盤介面 (Async Disk Sink)
 *
 * 職責：雙緩衝元件對外暴露 rotate() 給 matching thread 做指針翻轉（快），
 * drainToDisk() 給 flusher thread 做 ChronicleMap 寫入（慢，off critical path）。
 */
public interface DiskSink {
    /** 由 matching thread 呼叫：若 draining 緩衝已清空，將 active 翻轉為 draining。指針操作，ns 級。 */
    boolean rotate();

    /** 由 flusher thread 呼叫：將 draining 緩衝寫入 ChronicleMap，然後釋放。 */
    void drainToDisk();
}
