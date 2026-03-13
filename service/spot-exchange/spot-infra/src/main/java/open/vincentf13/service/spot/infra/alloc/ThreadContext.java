package open.vincentf13.service.spot.infra.alloc;

import lombok.Getter;
import open.vincentf13.service.spot.model.command.*;
import open.vincentf13.service.spot.sbe.*;

/**
 * 執行緒上下文總管 (Thread-Local Context Hub) - 懶加載優化版
 */
public class ThreadContext {
    private static final ThreadLocal<ThreadContext> INSTANCE = ThreadLocal.withInitial(ThreadContext::new);

    public static ThreadContext get() {
        return INSTANCE.get();
    }

    public static void cleanup() {
        ThreadContext ctx = INSTANCE.get();
        if (ctx != null) {
            ctx.scratchBuffer.release(); // 必須手動釋放堆外內存
            INSTANCE.remove(); // 切斷引用，其餘 POJO 由 GC 回收
        }
    }

    // --- 核心資源：預分配 (Hot) ---
    @Getter private final NativeUnsafeBuffer scratchBuffer = new NativeUnsafeBuffer(1024);
    @Getter private final RequestHolder requestHolder = new RequestHolder();

    // --- 指令數據載體：懶加載 (Lazy) ---
    private AuthCommand authCommand;
    private OrderCreateCommand orderCreateCommand;
    private OrderCancelCommand orderCancelCommand;
    private DepositCommand depositCommand;
    private SnapshotCommand snapshotCommand;
    private AuthReportWal authReportWal;
    private OrderAcceptedWal orderAcceptedWal;
    private OrderRejectedWal orderRejectedWal;
    private OrderCanceledWal orderCanceledWal;
    private OrderMatchWal orderMatchWal;

    public AuthCommand getAuthCommand() { if (authCommand == null) authCommand = new AuthCommand(); return authCommand; }
    public OrderCreateCommand getOrderCreateCommand() { if (orderCreateCommand == null) orderCreateCommand = new OrderCreateCommand(); return orderCreateCommand; }
    public OrderCancelCommand getOrderCancelCommand() { if (orderCancelCommand == null) orderCancelCommand = new OrderCancelCommand(); return orderCancelCommand; }
    public DepositCommand getDepositCommand() { if (depositCommand == null) depositCommand = new DepositCommand(); return depositCommand; }
    public SnapshotCommand getSnapshotCommand() { if (snapshotCommand == null) snapshotCommand = new SnapshotCommand(); return snapshotCommand; }
    public AuthReportWal getAuthReportWal() { if (authReportWal == null) authReportWal = new AuthReportWal(); return authReportWal; }
    public OrderAcceptedWal getOrderAcceptedWal() { if (orderAcceptedWal == null) orderAcceptedWal = new OrderAcceptedWal(); return orderAcceptedWal; }
    public OrderRejectedWal getOrderRejectedWal() { if (orderRejectedWal == null) orderRejectedWal = new OrderRejectedWal(); return orderRejectedWal; }
    public OrderCanceledWal getOrderCanceledWal() { if (orderCanceledWal == null) orderCanceledWal = new OrderCanceledWal(); return orderCanceledWal; }
    public OrderMatchWal getOrderMatchWal() { if (orderMatchWal == null) orderMatchWal = new OrderMatchWal(); return orderMatchWal; }

    // --- SBE 工具：懶加載 (Lazy) ---
    private MessageHeaderEncoder headerEncoder;
    private MessageHeaderDecoder headerDecoder;
    private OrderCreateEncoder orderCreateEncoder;
    private OrderCreateDecoder orderCreateDecoder;
    private OrderCancelEncoder orderCancelEncoder;
    private OrderCancelDecoder orderCancelDecoder;
    private AuthEncoder authEncoder;
    private AuthDecoder authDecoder;
    private DepositEncoder depositEncoder;
    private DepositDecoder depositDecoder;
    private SnapshotEncoder snapshotEncoder;
    private SnapshotDecoder snapshotDecoder;
    private ExecutionReportEncoder executionReportEncoder;
    private ExecutionReportDecoder executionReportDecoder;

    public MessageHeaderEncoder getHeaderEncoder() { if (headerEncoder == null) headerEncoder = new MessageHeaderEncoder(); return headerEncoder; }
    public MessageHeaderDecoder getHeaderDecoder() { if (headerDecoder == null) headerDecoder = new MessageHeaderDecoder(); return headerDecoder; }
    public OrderCreateEncoder getOrderCreateEncoder() { if (orderCreateEncoder == null) orderCreateEncoder = new OrderCreateEncoder(); return orderCreateEncoder; }
    public OrderCreateDecoder getOrderCreateDecoder() { if (orderCreateDecoder == null) orderCreateDecoder = new OrderCreateDecoder(); return orderCreateDecoder; }
    public OrderCancelEncoder getOrderCancelEncoder() { if (orderCancelEncoder == null) orderCancelEncoder = new OrderCancelEncoder(); return orderCancelEncoder; }
    public OrderCancelDecoder getOrderCancelDecoder() { if (orderCancelDecoder == null) orderCancelDecoder = new OrderCancelDecoder(); return orderCancelDecoder; }
    public AuthEncoder getAuthEncoder() { if (authEncoder == null) authEncoder = new AuthEncoder(); return authEncoder; }
    public AuthDecoder getAuthDecoder() { if (authDecoder == null) authDecoder = new AuthDecoder(); return authDecoder; }
    public DepositEncoder getDepositEncoder() { if (depositEncoder == null) depositEncoder = new DepositEncoder(); return depositEncoder; }
    public DepositDecoder getDepositDecoder() { if (depositDecoder == null) depositDecoder = new DepositDecoder(); return depositDecoder; }
    public SnapshotEncoder getSnapshotEncoder() { if (snapshotEncoder == null) snapshotEncoder = new SnapshotEncoder(); return snapshotEncoder; }
    public SnapshotDecoder getSnapshotDecoder() { if (snapshotDecoder == null) snapshotDecoder = new SnapshotDecoder(); return snapshotDecoder; }
    public ExecutionReportEncoder getExecutionReportEncoder() { if (executionReportEncoder == null) executionReportEncoder = new ExecutionReportEncoder(); return executionReportEncoder; }
    public ExecutionReportDecoder getExecutionReportDecoder() { if (executionReportDecoder == null) executionReportDecoder = new ExecutionReportDecoder(); return executionReportDecoder; }

    private ThreadContext() {}

    @lombok.Data
    public static class RequestHolder {
        private String op;
        private long userId, orderId, price, qty, cid, amount;
        private int symbolId, assetId;
        private String side;
        public void reset() { op = null; userId = orderId = price = qty = cid = amount = 0; symbolId = assetId = 0; side = null; }
    }
}
