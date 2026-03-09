package open.vincentf13.service.spot_exchange.infra;

import io.aeron.Aeron;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PreDestroy;

/** 
  Aeron 基礎設施配置
 */
@Configuration
public class AeronConfig {
    private MediaDriver driver;
    private Aeron aeron;

    @Bean
    public Aeron aeron() {
        driver = MediaDriver.launchEmbedded(new MediaDriver.Context()
                .threadingMode(ThreadingMode.SHARED)
                .dirDeleteOnStart(true));
        
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
