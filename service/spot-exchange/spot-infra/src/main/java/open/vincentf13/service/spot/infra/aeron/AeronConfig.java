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
 * Aeron 通訊配置
 * 職責：建立 MediaDriver + Aeron Client，注入全域實例。
 */
@Configuration
public class AeronConfig {

    private MediaDriver driver;
    private Aeron aeron;

    @Value("${aeron.dir}")
    private String aeronDir;

    @Value("${aeron.driver.enabled:true}")
    private boolean driverEnabled;

    @Bean
    public Aeron aeron() {
        if (driverEnabled) {
            var idle = new BusySpinIdleStrategy();
            driver = MediaDriver.launch(new MediaDriver.Context()
                    .aeronDirectoryName(aeronDir)
                    .threadingMode(ThreadingMode.DEDICATED)
                    .conductorIdleStrategy(idle)
                    .senderIdleStrategy(idle)
                    .receiverIdleStrategy(idle)
                    .termBufferSparseFile(false)
                    .publicationTermBufferLength(AeronConstants.DEFAULT_TERM_BUFFER_LENGTH)
                    .dirDeleteOnStart(true));
        }

        aeron = Aeron.connect(new Aeron.Context()
                .aeronDirectoryName(aeronDir)
                .preTouchMappedMemory(true));

        AeronUtil.setAeron(aeron);
        SigInt.register(this::close);
        return aeron;
    }

    @PreDestroy
    public void close() {
        if (aeron != null) { aeron.close(); aeron = null; }
        if (driver != null) { driver.close(); driver = null; }
    }
}
