package open.vincentf13.service.spot.infra.alloc;

import lombok.Getter;
import open.vincentf13.service.spot.infra.alloc.aeron.*;
import open.vincentf13.service.spot.model.command.*;
import open.vincentf13.service.spot.sbe.*;

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

    // --- 核心資源 ---
    @Getter private final NativeUnsafeBuffer scratchBuffer = new NativeUnsafeBuffer(1024);
    @Getter private final RequestHolder requestHolder = new RequestHolder();
    
    // --- Aeron 傳輸模型 (Fixed Layout) ---
    @Getter private final AeronAuth aeronAuth = new AeronAuth();
    @Getter private final AeronOrderCreate aeronOrderCreate = new AeronOrderCreate();
    @Getter private final AeronOrderCancel aeronOrderCancel = new AeronOrderCancel();
    @Getter private final AeronDeposit aeronDeposit = new AeronDeposit();
    @Getter private final AeronEnvelope aeronEnvelope = new AeronEnvelope();

    // --- 業務指令與回報模型 (Flyweight) ---
    private AuthCommand authCommand;
    private OrderCreateCommand orderCreateCommand;
    private OrderCancelCommand orderCancelCommand;
    private DepositCommand depositCommand;
    
    private AuthReport authReport;
    private OrderAcceptedReport orderAcceptedReport;
    private OrderRejectedReport orderRejectedReport;
    private OrderCanceledReport orderCanceledReport;
    private OrderMatchReport orderMatchReport;

    public AuthCommand getAuthCommand() { if (authCommand == null) authCommand = new AuthCommand(); return authCommand; }
    public OrderCreateCommand getOrderCreateCommand() { if (orderCreateCommand == null) orderCreateCommand = new OrderCreateCommand(); return orderCreateCommand; }
    public OrderCancelCommand getOrderCancelCommand() { if (orderCancelCommand == null) orderCancelCommand = new OrderCancelCommand(); return orderCancelCommand; }
    public DepositCommand getDepositCommand() { if (depositCommand == null) depositCommand = new DepositCommand(); return depositCommand; }
    
    public AuthReport getAuthReport() { if (authReport == null) authReport = new AuthReport(); return authReport; }
    public OrderAcceptedReport getOrderAcceptedReport() { if (orderAcceptedReport == null) orderAcceptedReport = new OrderAcceptedReport(); return orderAcceptedReport; }
    public OrderRejectedReport getOrderRejectedReport() { if (orderRejectedReport == null) orderRejectedReport = new OrderRejectedReport(); return orderRejectedReport; }
    public OrderCanceledReport getOrderCanceledReport() { if (orderCanceledReport == null) orderCanceledReport = new OrderCanceledReport(); return orderCanceledReport; }
    public OrderMatchReport getOrderMatchReport() { if (orderMatchReport == null) orderMatchReport = new OrderMatchReport(); return orderMatchReport; }

    // --- SBE 工具 (Lazy) ---
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
    public ExecutionReportEncoder getExecutionReportEncoder() { if (executionReportEncoder == null) executionReportEncoder = new ExecutionReportEncoder(); return executionReportEncoder; }
    public ExecutionReportDecoder getExecutionReportDecoder() { if (executionReportDecoder == null) executionReportDecoder = new ExecutionReportDecoder(); return executionReportDecoder; }

    private ThreadContext() {}

    /** 統一指令解析載體 (God Object for Performance) */
    @lombok.Data
    public static class RequestHolder {
        private String op;
        private long userId, orderId, price, qty, cid, amount;
        private int symbolId, assetId;
        private String side;
        public void reset() { op = null; userId = orderId = price = qty = cid = amount = 0; symbolId = assetId = 0; side = null; }
    }
}
