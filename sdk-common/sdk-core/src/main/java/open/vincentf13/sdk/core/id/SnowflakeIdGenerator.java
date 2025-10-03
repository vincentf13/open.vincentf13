package open.vincentf13.sdk.core.id;

/**
 * 雪花算法 ID 生成器（64 位長整型）
 *
 * 結構 (64 bits)：
 * 0 - 41 位時間戳（毫秒） - 5 位資料中心ID - 5 位工作節點ID - 12 位序列號
 *
 * 特點：
 * - 單節點每毫秒可生成 4096 個 ID
 * - 單機內執行緒安全
 * - 分佈式場景可依節點/機房分配 workerId、datacenterId
 *
 * 使用範例：
 * <pre>{@code
 * SnowflakeIdGenerator idGen = new SnowflakeIdGenerator(1, 1);
 * long id = idGen.nextId();   // 產生唯一ID
 * }</pre>
 */
public final class SnowflakeIdGenerator {

    // ==== 配置常數 ====
    private static final long EPOCH = 1609459200000L; // 2021-01-01 作為起始時間戳

    private static final long WORKER_ID_BITS = 5L;       // 節點ID長度
    private static final long DATACENTER_ID_BITS = 5L;   // 機房ID長度
    private static final long SEQUENCE_BITS = 12L;       // 毫秒內序列號

    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
    private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS);

    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    private static final long TIMESTAMP_LEFT_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;

    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

    // ==== 節點資訊 ====
    private final long workerId;
    private final long datacenterId;

    // ==== 狀態 ====
    private long lastTimestamp = -1L;
    private long sequence = 0L;

    /**
     * @param datacenterId 機房ID (0~31)
     * @param workerId     節點ID (0~31)
     */
    public SnowflakeIdGenerator(long datacenterId, long workerId) {
        if (workerId > MAX_WORKER_ID || workerId < 0)
            throw new IllegalArgumentException("workerId out of range 0~" + MAX_WORKER_ID);
        if (datacenterId > MAX_DATACENTER_ID || datacenterId < 0)
            throw new IllegalArgumentException("datacenterId out of range 0~" + MAX_DATACENTER_ID);
        this.workerId = workerId;
        this.datacenterId = datacenterId;
    }

    /**
     * 生成下一個唯一 ID（執行緒安全）
     */
    public synchronized long nextId() {
        long timestamp = currentTime();

        if (timestamp < lastTimestamp) {
            throw new IllegalStateException("Clock moved backwards: " + (lastTimestamp - timestamp) + "ms");
        }

        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            // 同毫秒序列耗盡，阻塞到下一毫秒
            if (sequence == 0) timestamp = waitNextMillis(timestamp);
        } else {
            sequence = 0L; // 新毫秒序列重置
        }

        lastTimestamp = timestamp;

        // 拼接位元 (timestampDiff << 22) | (datacenter << 17) | (worker << 12) | seq
        return ((timestamp - EPOCH) << TIMESTAMP_LEFT_SHIFT)
                | (datacenterId << DATACENTER_ID_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    private long waitNextMillis(long currentTs) {
        long ts = currentTime();
        while (ts <= currentTs) ts = currentTime();
        return ts;
    }

    private long currentTime() {
        return System.currentTimeMillis();
    }
}
