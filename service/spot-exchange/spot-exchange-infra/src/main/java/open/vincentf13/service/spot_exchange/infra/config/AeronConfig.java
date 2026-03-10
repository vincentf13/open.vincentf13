package open.vincentf13.service.spot_exchange.infra.config;

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

    @Value("${aeron.dir:data/spot-exchange/aeron}")
    private String aeronDir;

    @Value("${aeron.inbound.channel:aeron:udp?endpoint=localhost:40444}")
    private String inboundChannel;

    @Value("${aeron.outbound.channel:aeron:udp?endpoint=localhost:40445}")
    private String outboundChannel;

    @Value("${aeron.threading-mode:SHARED}")
    private String threadingMode;

    @Bean
    public Aeron aeron() {
        // --- 深度優化：指定固定目錄，避免多服務啟動衝突 ---
        MediaDriver.Context ctx = new MediaDriver.Context()
                .threadingMode(ThreadingMode.valueOf(threadingMode))
                .aeronDirectoryName(aeronDir)
                .dirDeleteOnStart(true); 

        driver = MediaDriver.launchEmbedded(ctx);
        
        aeron = Aeron.connect(new Aeron.Context()
                .aeronDirectoryName(driver.aeronDirectoryName()));
        return aeron;
    }

    public String getInboundChannel() { return inboundChannel; }
    public String getOutboundChannel() { return outboundChannel; }

    @PreDestroy
    public void close() {
        if (aeron != null) aeron.close();
        if (driver != null) driver.close();
    }
}
