package open.vincentf13.service.spot.infra.aeron;

import io.aeron.Aeron;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.agrona.concurrent.SigInt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PreDestroy;

/**
 * Aeron 生產級通訊配置與常數定義
 * 職責：整合 MediaDriver 運行時配置與傳輸層物理常數
 */
@Configuration
public class Config {

    // --- Aeron 傳輸層常數 (原 AeronConstants) ---
    
    /** WAL 讀取批次大小 (每次 doWork 處理的最大指令數) */
    public static final int WAL_BATCH_SIZE = 1000;

    /** 指標統計批次大小 (每處理 N 筆訊息更新一次 MetricsCollector) */
    public static final int METRICS_BATCH_SIZE = 5000;

    /** Aeron 訂閱輪詢限制 (每次 poll 獲取的最大 Fragment 數) */
    public static final int AERON_POLL_LIMIT = 10;

    /** RESUME 握手信號的總長度 (MsgType:4 bytes + Seq:8 bytes) */
    public static final int RESUME_SIGNAL_LENGTH = 12;

    /** RESUME 握手信號發送間隔 (毫秒)，防止頻繁發送導致控制端擁塞 */
    public static final int RESUME_SIGNAL_INTERVAL_MS = 200;

    /** 訊息序列號 (Sequence) 在 DirectBuffer 中的偏移量 */
    public static final int MSG_SEQ_OFFSET = 4;

    /** 進度持久化週期 (每處理 N 筆訊息強制寫入一次 ChronicleMap 進度) */
    public static final int METADATA_FLUSH_PERIOD = 100;

    /** 背壓 (Back-pressure) 重試次數限制，超過後判定為發送失敗 */
    public static final int BACK_PRESSURE_RETRY_LIMIT = 1000;

    /** 預設 Term Buffer 長度 (64MB)，用於在高吞吐下緩衝訊息，減少背壓 */
    public static final int DEFAULT_TERM_BUFFER_LENGTH = 64 * 1024 * 1024;

    // --- Spring Bean 配置 ---

    private MediaDriver driver;
    private Aeron aeron;

    @Value("${aeron.dir}")
    private String aeronDir;

    @Value("${aeron.driver.enabled:true}")
    private boolean driverEnabled;

    @Bean
    public Aeron aeron() {
        if (driverEnabled) {
            MediaDriver.Context driverCtx = new MediaDriver.Context()
                    .aeronDirectoryName(aeronDir)
                    .threadingMode(ThreadingMode.DEDICATED)
                    .conductorIdleStrategy(open.vincentf13.service.spot.infra.thread.Strategies.BUSY_SPIN)
                    .senderIdleStrategy(open.vincentf13.service.spot.infra.thread.Strategies.BUSY_SPIN)
                    .receiverIdleStrategy(open.vincentf13.service.spot.infra.thread.Strategies.BUSY_SPIN)
                    .termBufferSparseFile(false)
                    .publicationTermBufferLength(DEFAULT_TERM_BUFFER_LENGTH)
                    .dirDeleteOnStart(true);

            driver = MediaDriver.launch(driverCtx);
        }
        
        Aeron.Context clientCtx = new Aeron.Context()
                .aeronDirectoryName(aeronDir)
                .preTouchMappedMemory(true);

        aeron = Aeron.connect(clientCtx);
        
        SigInt.register(() -> {
            if (aeron != null) aeron.close();
            if (driver != null) driver.close();
        });
        return aeron;
    }

    @PreDestroy
    public void close() {
        if (aeron != null) aeron.close();
        if (driver != null) driver.close();
    }
}
