package open.vincentf13.sdk.infra.mysql.config;

import open.vincentf13.sdk.infra.mysql.typehandler.EnumToStringTypeHandler;
import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

/**
 * Registers the global enum type handler so enums persist as strings.
 */
@AutoConfiguration(after = MysqlMapperScanAutoConfiguration.class)
@ConditionalOnClass(ConfigurationCustomizer.class)
public class EnumTypeHandlerAutoConfiguration {

    @Bean
    public ConfigurationCustomizer enumTypeHandlerCustomizer() {
        return configuration -> configuration.setDefaultEnumTypeHandler(EnumToStringTypeHandler.class);
    }
}
