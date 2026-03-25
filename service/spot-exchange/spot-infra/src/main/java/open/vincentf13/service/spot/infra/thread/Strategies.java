package open.vincentf13.service.spot.infra.thread;

import org.agrona.concurrent.BusySpinIdleStrategy;
import org.agrona.concurrent.IdleStrategy;

/**
 * 全局併發策略池 (Strategies)
 * 職責：提供無狀態、可複用的併發組件 (如 IdleStrategy)，減少對象創建開銷。
 */
public class Strategies {

    /** 
     * 全局唯一的忙等策略 (BusySpinIdleStrategy)
     * 無狀態且線程安全，適合在 Dedicated Thread 中廣泛複用。
     */
    public static final IdleStrategy BUSY_SPIN = new BusySpinIdleStrategy();

    private Strategies() {}
}
