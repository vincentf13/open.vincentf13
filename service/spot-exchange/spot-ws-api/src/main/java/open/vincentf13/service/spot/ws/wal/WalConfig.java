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

    private static final int RING_BUFFER_SIZE = 1 << 16; // 65536

    @Bean
    public RingBuffer<WalEvent> walRingBuffer() {
        return RingBuffer.createMultiProducer(
                WalEvent::new,
                RING_BUFFER_SIZE,
                new BusySpinWaitStrategy());
    }
}
