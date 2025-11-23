package open.vincentf13.sdk.core.id;

import com.github.yitter.contract.IdGeneratorOptions;
import com.github.yitter.idgen.DefaultIdGenerator;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.sdk.core.config.SnowflakeIdProperties;
import open.vincentf13.sdk.core.OpenLog;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SnowflakeIdProperties.class)
@Slf4j
public class IdGeneratorAutoConfiguration {

    @Bean
    public DefaultIdGenerator snowflakeIdGenerator(SnowflakeIdProperties snowflakeIdProperties) {
        OpenLog.info(log,"Bean Config","DefaultIdGenerator","worker id" , snowflakeIdProperties.getWorkerId());
        IdGeneratorOptions options = new IdGeneratorOptions(snowflakeIdProperties.getWorkerId());
        return new DefaultIdGenerator(options);
    }
}
