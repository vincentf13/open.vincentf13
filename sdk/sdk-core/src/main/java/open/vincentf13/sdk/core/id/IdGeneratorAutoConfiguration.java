package open.vincentf13.sdk.core.id;

import com.github.yitter.contract.IdGeneratorOptions;
import com.github.yitter.idgen.DefaultIdGenerator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SnowflakeIdProperties.class)
public class IdGeneratorAutoConfiguration {
    
    @Bean
    public DefaultIdGenerator snowflakeIdGenerator(SnowflakeIdProperties snowflakeIdProperties) {
        IdGeneratorOptions options = new IdGeneratorOptions(snowflakeIdProperties.getWorkerId());
        return new DefaultIdGenerator(options);
    }
}
