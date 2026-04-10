package open.vincentf13.service.spot.ws.wal;

import com.lmax.disruptor.BusySpinWaitStrategy;
import com.lmax.disruptor.RingBuffer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * WAL Disruptor RingBuffer 配置
 *
 * Multi-Producer (Netty workers) → Single-Consumer (WalWriter)
 * 65536 slots × ~256B/slot ≈ 16MB 預分配，Zero-GC。
 */
@Configuration
public class WalConfig {

    private static final int RING_BUFFER_SIZE = 1 << 14; // 16384

    @Bean
    public RingBuffer<WalEvent> walRingBuffer(@org.springframework.beans.factory.annotation.Value("${netty.worker.count:2}") int workerCount) {
        if (workerCount == 1) {
            return RingBuffer.createSingleProducer(
                    WalEvent::new,
                    RING_BUFFER_SIZE,
                    new BusySpinWaitStrategy());
        }
        return RingBuffer.createMultiProducer(
                WalEvent::new,
                RING_BUFFER_SIZE,
                new BusySpinWaitStrategy());
    }
}
