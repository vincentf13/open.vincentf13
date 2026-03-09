package open.vincentf13.service.spot_exchange.infra;

import io.aeron.Aeron;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import org.agrona.concurrent.SigInt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PreDestroy;
import java.io.File;

@Configuration
public class AeronConfig {
    private MediaDriver driver;
    private Aeron aeron;

    @Value("${aeron.dir:data/spot_exchange/aeron}")
    private String aeronDir;

    @Bean
    public Aeron aeron() {
        // --- 深度優化：指定固定目錄，避免多服務啟動衝突 ---
        MediaDriver.Context ctx = new MediaDriver.Context()
                .threadingMode(ThreadingMode.SHARED)
                .aeronDirectoryName(aeronDir)
                .dirDeleteOnStart(true); // 注意：生產環境應由外部獨立啟動 MediaDriver

        driver = MediaDriver.launchEmbedded(ctx);
        
        aeron = Aeron.connect(new Aeron.Context()
                .aeronDirectoryName(driver.aeronDirectoryName()));
        return aeron;
    }

    @PreDestroy
    public void close() {
        if (aeron != null) aeron.close();
        if (driver != null) driver.close();
    }
}
