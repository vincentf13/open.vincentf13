package open.vincentf13.service.spot.infra.aeron;

import io.aeron.Aeron;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import org.agrona.concurrent.SigInt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PreDestroy;

@Configuration
public class Config {
    private MediaDriver driver;
    private Aeron aeron;

    @Value("${aeron.dir:data/spot-exchange/aeron}")
    private String aeronDir;

    @Bean
    public Aeron aeron() {
        MediaDriver.Context driverCtx = new MediaDriver.Context()
                .aeronDirectoryName(aeronDir)
                .threadingMode(ThreadingMode.SHARED);
        driver = MediaDriver.launch(driverCtx);
        
        Aeron.Context aeronCtx = new Aeron.Context()
                .aeronDirectoryName(aeronDir);
        aeron = Aeron.connect(aeronCtx);
        
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
