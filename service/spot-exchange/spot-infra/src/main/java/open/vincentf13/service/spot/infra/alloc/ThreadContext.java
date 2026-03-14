package open.vincentf13.service.spot.infra.alloc;

import lombok.Getter;
import open.vincentf13.service.spot.model.Order;
import open.vincentf13.service.spot.model.CidKey;
import open.vincentf13.service.spot.model.command.*;

/**
 * 執行緒上下文總管 (Thread-Local Context Hub)
 */
public class ThreadContext {
    private static final ThreadLocal<ThreadContext> INSTANCE = ThreadLocal.withInitial(ThreadContext::new);

    public static ThreadContext get() { return INSTANCE.get(); }

    public static void cleanup() {
        ThreadContext ctx = INSTANCE.get();
        if (ctx != null) {
            ctx.scratchBuffer.release();
            INSTANCE.remove();
        }
    }

    @Getter @lombok.Setter private long currentGatewaySequence = -1L;

    // --- 核心資源 ---
    @Getter private final NativeUnsafeBuffer scratchBuffer = new NativeUnsafeBuffer(1024);
    @Getter private final RequestHolder requestHolder = new RequestHolder();
    
    // --- 業務模型 (Flyweight / 池化物件) ---
    private AuthCommand authCommand;
    private OrderCreateCommand orderCreateCommand;
    private OrderCancelCommand orderCancelCommand;
    private DepositCommand depositCommand;

    private AuthReport authReport;
    private DepositReport depositReport;
    private ExecutionReport executionReport; // 統一回報對象

    // --- 緩衝物件 ---
    @Getter private final Order reusableOrder = new Order();

    public AuthCommand getAuthCommand() { if (authCommand == null) authCommand = new AuthCommand(); return authCommand; }
    public OrderCreateCommand getOrderCreateCommand() { if (orderCreateCommand == null) orderCreateCommand = new OrderCreateCommand(); return orderCreateCommand; }
    public OrderCancelCommand getOrderCancelCommand() { if (orderCancelCommand == null) orderCancelCommand = new OrderCancelCommand(); return orderCancelCommand; }
    public DepositCommand getDepositCommand() { if (depositCommand == null) depositCommand = new DepositCommand(); return depositCommand; }

    public AuthReport getAuthReport() { if (authReport == null) authReport = new AuthReport(); return authReport; }
    public DepositReport getDepositReport() { if (depositReport == null) depositReport = new DepositReport(); return depositReport; }
    public ExecutionReport getExecutionReport() { if (executionReport == null) executionReport = new ExecutionReport(); return executionReport; }

    private ThreadContext() {}

    /** 統一指令解析載體 (God Object for Performance) */
    @lombok.Data
    public static class RequestHolder {
        private String op;
        private long userId, orderId, price, qty, cid, amount;
        private int symbolId, assetId;
        private String side;
        private final CidKey cidKey = new CidKey();
        public void reset() { op = null; userId = orderId = price = qty = cid = amount = 0; symbolId = assetId = 0; side = null; }
    }
}
