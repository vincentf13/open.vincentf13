package open.vincentf13.service.spot.infra.aeron;

import io.aeron.Aeron;
import org.agrona.concurrent.SigInt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PreDestroy;

/**
 * Aeron Client 配置
 * 連接外部獨立 Media Driver，注入全域 Aeron 實例。
 */
@Configuration
public class AeronConfig {

    private Aeron aeron;

    @Value("${aeron.dir}")
    private String aeronDir;

    @Bean
    public Aeron aeron() {
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
    }
}
