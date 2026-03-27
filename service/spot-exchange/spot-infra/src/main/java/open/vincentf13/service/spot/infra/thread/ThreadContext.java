package open.vincentf13.service.spot.infra.thread;

import lombok.Getter;
import net.openhft.chronicle.bytes.PointerBytesStore;
import net.openhft.chronicle.bytes.Bytes;
import open.vincentf13.service.spot.infra.alloc.NativeUnsafeBuffer;
import open.vincentf13.service.spot.model.Order;
import open.vincentf13.service.spot.model.CidKey;
import open.vincentf13.service.spot.model.command.*;

/**
 * 執行緒上下文總管 (Thread-Local Context Hub)
 * 職責：維護執行緒專屬的資源與 指令模型池
 */
public class ThreadContext {
    private static final ThreadLocal<ThreadContext> INSTANCE = ThreadLocal.withInitial(ThreadContext::new);

    public static ThreadContext get() { return INSTANCE.get(); }

    public static void cleanup() {
        if (INSTANCE.get() != null) INSTANCE.remove();
    }

    // --- 核心資源 ---
    @Getter private final NativeUnsafeBuffer scratchBuffer = new NativeUnsafeBuffer(1024);
    @Getter private final Bytes<?> scratchBytes;
    
    // --- 指令模型 (Flyweight / 池化物件) ---
    private AuthCommand authCommand;
    private OrderCreateCommand orderCreateCommand;
    private OrderCancelCommand orderCancelCommand;
    private DepositCommand depositCommand;

    // --- 基礎組件 (Flyweight) ---
    @Getter private final Order reusableOrder = new Order();
    @Getter private final PointerBytesStore reusablePointer = new PointerBytesStore();
    @Getter private final CidKey cidKey = new CidKey();

    private ThreadContext() {
        PointerBytesStore pbs = new PointerBytesStore();
        pbs.set(scratchBuffer.addressOffset(), scratchBuffer.capacity());
        this.scratchBytes = pbs.bytesForWrite();
    }

    public AuthCommand getAuthCommand() { if (authCommand == null) authCommand = new AuthCommand(); return authCommand; }
    public OrderCreateCommand getOrderCreateCommand() { if (orderCreateCommand == null) orderCreateCommand = new OrderCreateCommand(); return orderCreateCommand; }
    public OrderCancelCommand getOrderCancelCommand() { if (orderCancelCommand == null) orderCancelCommand = new OrderCancelCommand(); return orderCancelCommand; }
    public DepositCommand getDepositCommand() { if (depositCommand == null) depositCommand = new DepositCommand(); return depositCommand; }
}
