package open.vincentf13.service.spot.infra.aeron;

import io.aeron.Aeron;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import open.vincentf13.service.spot.infra.thread.Strategies;
import org.agrona.concurrent.SigInt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PreDestroy;

/**
 * Aeron 生產級通訊配置 (AeronConfig)
 * 職責：整合 MediaDriver 運行時配置與 Aeron 實例注入
 */
@Configuration
public class AeronConfig {

    private MediaDriver driver;

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
                    .conductorIdleStrategy(Strategies.BUSY_SPIN)
                    .senderIdleStrategy(Strategies.BUSY_SPIN)
                    .receiverIdleStrategy(Strategies.BUSY_SPIN)
                    .termBufferSparseFile(false)
                    .publicationTermBufferLength(AeronConstants.DEFAULT_TERM_BUFFER_LENGTH)
                    .dirDeleteOnStart(true);

            driver = MediaDriver.launch(driverCtx);
        }
        
        Aeron.Context clientCtx = new Aeron.Context()
                .aeronDirectoryName(aeronDir)
                .preTouchMappedMemory(true);

        Aeron aeron = Aeron.connect(clientCtx);
        AeronClientHolder.setAeron(aeron);
        
        SigInt.register(() -> {
            if (aeron != null) aeron.close();
            if (driver != null) driver.close();
        });
        return aeron;
    }

    @PreDestroy
    public void close() {
        Aeron aeron = AeronClientHolder.aeron();
        if (aeron != null) aeron.close();
        if (driver != null) driver.close();
    }
}
