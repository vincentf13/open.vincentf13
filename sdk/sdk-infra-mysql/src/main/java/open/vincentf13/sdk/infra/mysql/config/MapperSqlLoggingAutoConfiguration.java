package open.vincentf13.sdk.infra.mysql.config;

import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

/**
 * 啟動時將所有 Mapper 所在套件的 log level 設為 DEBUG，方便追蹤 SQL。
 * 可透過 open.vincentf13.mybatis.mapper-sql-logging.enabled 控制是否啟用。
 */
@AutoConfiguration(after = MysqlMapperScanAutoConfiguration.class)
@ConditionalOnProperty(name = "open.vincentf13.mybatis.mapper-sql-logging.enabled", havingValue = "true")
public class MapperSqlLoggingAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MapperSqlLoggingAutoConfiguration.class);

    private static final String PROPERTY_ENABLED = "open.vincentf13.mybatis.mapper-sql-logging.enabled";

    @Bean
    ApplicationRunner mapperSqlLoggingInitializer(ObjectProvider<SqlSessionFactory> sqlSessionFactoryProvider,
                                                  LoggingSystem loggingSystem) {
        return args -> {
            Set<String> mapperPackages = new LinkedHashSet<>();
            sqlSessionFactoryProvider.stream().forEach(factory ->
                    factory.getConfiguration().getMapperRegistry().getMappers().forEach(mapperClass -> {
                        String packageName = mapperClass.getPackageName();
                        if (StringUtils.hasText(packageName)) {
                            mapperPackages.add(packageName);
                        }
                    })
            );

            for (String packageName : mapperPackages) {
                loggingSystem.setLogLevel(packageName, LogLevel.DEBUG);
                if (log.isDebugEnabled()) {
                    log.debug("Enabled DEBUG logging for mapper package: {}", packageName);
                }
            }
        };
    }
}
