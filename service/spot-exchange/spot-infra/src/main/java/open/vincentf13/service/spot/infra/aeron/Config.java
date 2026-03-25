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
  Aeron 生產級通訊配置
  採用 DEDICATED 線程模式與 BusySpin 策略以追求極致的微秒級延遲
 */
@Configuration
public class Config {
    private MediaDriver driver;
    private Aeron aeron;

    @Value("${aeron.dir}")
    private String aeronDir;

    @Value("${aeron.driver.enabled:true}")
    private boolean driverEnabled;

    @Bean
    public Aeron aeron() {
        if (driverEnabled) {
            // 1. 配置 MediaDriver Context (通訊引擎核心)
            MediaDriver.Context driverCtx = new MediaDriver.Context()
                    .aeronDirectoryName(aeronDir)
                    // 採用 DEDICATED 模式：Conductor, Sender, Receiver 各佔用獨立 CPU 核心
                    .threadingMode(ThreadingMode.DEDICATED)
                    // 全路徑使用 BusySpin 策略，消除線程切換延遲
                    .conductorIdleStrategy(new BusySpinIdleStrategy())
                    .senderIdleStrategy(new BusySpinIdleStrategy())
                    .receiverIdleStrategy(new BusySpinIdleStrategy())
                    // 預先分配實體空間，防止寫入時的磁碟碎片
                    .termBufferSparseFile(false)
                    // 設定全域 Term Buffer 為 64MB，減少高頻交易下的背壓機率
                    .publicationTermBufferLength(AeronConstants.DEFAULT_TERM_BUFFER_LENGTH)
                    .dirDeleteOnStart(true);

            driver = MediaDriver.launch(driverCtx);
        }
        
        // 2. 配置 Aeron Client Context
        Aeron.Context clientCtx = new Aeron.Context()
                .aeronDirectoryName(aeronDir)
                // 開啟記憶體分頁預熱，確保讀寫時不會因 Page Fault 產生延遲尖峰
                .preTouchMappedMemory(true);

        aeron = Aeron.connect(clientCtx);
        
        // 註冊系統信號，確保安全關閉
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
