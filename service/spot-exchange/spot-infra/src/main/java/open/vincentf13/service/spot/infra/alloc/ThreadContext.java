package open.vincentf13.service.spot.infra.alloc;

import lombok.Getter;
import open.vincentf13.service.spot.infra.alloc.NativeUnsafeBuffer;
import open.vincentf13.service.spot.sbe.MessageHeaderDecoder;
import open.vincentf13.service.spot.sbe.MessageHeaderEncoder;
import open.vincentf13.service.spot.sbe.OrderCreateEncoder;
import open.vincentf13.service.spot.sbe.ExecutionReportEncoder;
import open.vincentf13.service.spot.sbe.OrderCreateDecoder;
import open.vincentf13.service.spot.sbe.ExecutionReportDecoder;

/**
 * 執行緒上下文總管 (Thread-Local Context Hub)
 * 
 * <p>【設計職責】</p>
 * <ul>
 *   <li>1. <b>資源整合</b>：統一管理原本散落在各類別中的 {@code ThreadLocal}，提高復用率。</li>
 *   <li>2. <b>內存管控</b>：集中持有 {@link NativeUnsafeBuffer}，並提供顯式釋放方法。</li>
 *   <li>3. <b>Zero-GC 輔助</b>：預實例化常用的 SBE 編解碼器，避免在熱點路徑重複建立物件。</li>
 * </ul>
 */
@Getter
public class ThreadContext {
    private static final ThreadLocal<ThreadContext> INSTANCE = ThreadLocal.withInitial(ThreadContext::new);

    /** 獲取當前執行緒的上下文實例 */
    public static ThreadContext get() {
        return INSTANCE.get();
    }

    /** 顯式清理當前執行緒的所有資源 (防範 Direct Memory 洩漏與 ThreadLocal 洩漏) */
    public static void cleanup() {
        ThreadContext ctx = INSTANCE.get();
        if (ctx != null) {
            ctx.scratchBuffer.release();
            INSTANCE.remove();
        }
    }

    // --- 集中管理的資源清單 ---

    /** 
     * 通用「草稿緩衝區」(Scratch Buffer)
     * <p>用於 SBE 編解碼、JSON 解析、或任何臨時的二進位數據操作，初始容量 1024 Byte，可彈性擴展。</p>
     */
    private final NativeUnsafeBuffer scratchBuffer = new NativeUnsafeBuffer(1024);

    /** 通用請求暫存器 (用於解析 JSON 指令) */
    private final RequestHolder requestHolder = new RequestHolder();

    // --- SBE 編解碼器 (無狀態，Wrap 後即可使用) ---
    private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    private final OrderCreateEncoder orderCreateEncoder = new OrderCreateEncoder();
    private final OrderCreateDecoder orderCreateDecoder = new OrderCreateDecoder();
    private final ExecutionReportEncoder executionReportEncoder = new ExecutionReportEncoder();
    private final ExecutionReportDecoder executionReportDecoder = new ExecutionReportDecoder();

    /** 
     * 私有構造函數，僅由 ThreadLocal 初始化
     */
    private ThreadContext() {
    }

    /**
     * 請求暫存器實體類 (Zero-GC 設計，重複使用字段)
     */
    @lombok.Data
    public static class RequestHolder {
        private String op;
        private long userId;
        private long orderId;
        private int symbolId;
        private long price;
        private long qty;
        private String side;
        private long cid;
        private int assetId;
        private long amount;

        public void reset() {
            op = null;
            userId = 0;
            orderId = 0;
            symbolId = 0;
            price = 0;
            qty = 0;
            side = null;
            cid = 0;
            assetId = 0;
            amount = 0;
        }
    }
}
