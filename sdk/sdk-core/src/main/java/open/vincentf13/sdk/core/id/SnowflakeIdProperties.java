package open.vincentf13.sdk.core.id;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "open.vincentf13.id-generator.snowflake")
public class SnowflakeIdProperties {
    /**
     Worker ID for Snowflake algorithm. Must be unique for each instance.
     <p>
     This value can be configured via the application property
     {@code open.vincentf13.id-generator.snowflake.worker-id}
     or the environment variable
     {@code OPEN_VINCENTF13_IDGENERATOR_SNOWFLAKE_WORKERID}.
     <p>
     Defaults to 0 if not specified.
     */
    private short workerId = 0;
}
