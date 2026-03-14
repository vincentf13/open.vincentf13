package open.vincentf13.service.spot.infra.alloc;

import lombok.Getter;
import open.vincentf13.service.spot.infra.alloc.aeron.*;
import open.vincentf13.service.spot.model.Order;
import open.vincentf13.service.spot.model.CidKey;
import open.vincentf13.service.spot.model.command.*;

/**
 * 執行緒上下文總管 (Thread-Local Context Hub)
 * 職責：維護執行緒專屬的資源與 Flyweight 物件池
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
    
    // --- 緩衝物件 (用於零分配讀取) ---
    @Getter private final Order reusableOrder = new Order();
    
    // --- Aeron 傳輸模型 (Fixed Layout) ---
    @Getter private final AeronAuth aeronAuth = new AeronAuth();
    @Getter private final AeronOrderCreate aeronOrderCreate = new AeronOrderCreate();
    @Getter private final AeronOrderCancel aeronOrderCancel = new AeronOrderCancel();
    @Getter private final AeronDeposit aeronDeposit = new AeronDeposit();
    @Getter private final AeronEnvelope aeronEnvelope = new AeronEnvelope();

    // --- 業務指令與回報模型 (Flyweight / 池化物件) ---
    // 這些物件內部封裝了自身的 SBE Encoder/Decoder，不再依賴 ThreadContext 全局變量
    private AuthCommand authCommand;
    private OrderCreateCommand orderCreateCommand;
    private OrderCancelCommand orderCancelCommand;
    private DepositCommand depositCommand;

    private AuthReport authReport;
    private DepositReport depositReport;
    private OrderAcceptedReport orderAcceptedReport;
    private OrderRejectedReport orderRejectedReport;
    private OrderCanceledReport orderCanceledReport;
    private OrderMatchReport orderMatchReport;

    public AuthCommand getAuthCommand() { if (authCommand == null) authCommand = new AuthCommand(); return authCommand; }
    public OrderCreateCommand getOrderCreateCommand() { if (orderCreateCommand == null) orderCreateCommand = new OrderCreateCommand(); return orderCreateCommand; }
    public OrderCancelCommand getOrderCancelCommand() { if (orderCancelCommand == null) orderCancelCommand = new OrderCancelCommand(); return orderCancelCommand; }
    public DepositCommand getDepositCommand() { if (depositCommand == null) depositCommand = new DepositCommand(); return depositCommand; }

    public AuthReport getAuthReport() { if (authReport == null) authReport = new AuthReport(); return authReport; }
    public DepositReport getDepositReport() { if (depositReport == null) depositReport = new DepositReport(); return depositReport; }
    public OrderAcceptedReport getOrderAcceptedReport() { if (orderAcceptedReport == null) orderAcceptedReport = new OrderAcceptedReport(); return orderAcceptedReport; }
    public OrderRejectedReport getOrderRejectedReport() { if (orderRejectedReport == null) orderRejectedReport = new OrderRejectedReport(); return orderRejectedReport; }
    public OrderCanceledReport getOrderCanceledReport() { if (orderCanceledReport == null) orderCanceledReport = new OrderCanceledReport(); return orderCanceledReport; }
    public OrderMatchReport getOrderMatchReport() { if (orderMatchReport == null) orderMatchReport = new OrderMatchReport(); return orderMatchReport; }

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
