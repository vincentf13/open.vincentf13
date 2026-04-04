package open.vincentf13.service.spot.infra.thread;

import lombok.Getter;
import net.openhft.chronicle.bytes.PointerBytesStore;
import net.openhft.chronicle.bytes.Bytes;
import open.vincentf13.service.spot.model.Order;
import open.vincentf13.service.spot.model.CidKey;
import open.vincentf13.service.spot.model.command.*;
import org.agrona.concurrent.UnsafeBuffer;

import java.nio.ByteBuffer;

/**
 * 執行緒上下文 (Thread-Local Context)
 * 職責：維護執行緒專屬的可重用資源與指令模型池，實現 Zero-GC。
 */
public class ThreadContext {
    private static final ThreadLocal<ThreadContext> INSTANCE = ThreadLocal.withInitial(ThreadContext::new);

    public static ThreadContext get() { return INSTANCE.get(); }
    public static void cleanup() { INSTANCE.remove(); }

    // 1KB 堆外暫存緩衝 (原 NativeUnsafeBuffer，內聯化)
    private final ByteBuffer rawBuffer = ByteBuffer.allocateDirect(1024);
    private final UnsafeBuffer unsafeBuffer = new UnsafeBuffer(rawBuffer);
    @Getter private final Bytes<?> scratchBytes;

    // 享元模型
    @Getter private final Order reusableOrder = new Order();
    @Getter private final PointerBytesStore reusablePointer = new PointerBytesStore();
    @Getter private final CidKey cidKey = new CidKey();

    // 指令模型 (lazy init)
    private AuthCommand authCommand;
    private OrderCreateCommand orderCreateCommand;
    private OrderCancelCommand orderCancelCommand;
    private DepositCommand depositCommand;

    private ThreadContext() {
        PointerBytesStore pbs = new PointerBytesStore();
        pbs.set(unsafeBuffer.addressOffset(), unsafeBuffer.capacity());
        this.scratchBytes = pbs.bytesForWrite();
    }

    public AuthCommand getAuthCommand() { if (authCommand == null) authCommand = new AuthCommand(); return authCommand; }
    public OrderCreateCommand getOrderCreateCommand() { if (orderCreateCommand == null) orderCreateCommand = new OrderCreateCommand(); return orderCreateCommand; }
    public OrderCancelCommand getOrderCancelCommand() { if (orderCancelCommand == null) orderCancelCommand = new OrderCancelCommand(); return orderCancelCommand; }
    public DepositCommand getDepositCommand() { if (depositCommand == null) depositCommand = new DepositCommand(); return depositCommand; }
}
