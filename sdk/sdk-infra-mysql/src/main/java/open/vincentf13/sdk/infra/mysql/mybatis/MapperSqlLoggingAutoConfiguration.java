package open.vincentf13.sdk.infra.mysql.mybatis;

import java.util.LinkedHashSet;
import java.util.Set;
import open.vincentf13.sdk.core.log.OpenLog;
import open.vincentf13.sdk.infra.mysql.MysqlEvent;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

/**
 * 啟動時將所有 Mapper 所在套件的 log level 設為 DEBUG，方便追蹤 SQL。 可透過
 * open.vincentf13.mybatis.mapper-sql-logging.enabled 控制是否啟用。
 */
@AutoConfiguration(after = MysqlMapperScanAutoConfiguration.class)
@ConditionalOnProperty(
    name = "open.vincentf13.mybatis.mapper-sql-logging.enabled",
    havingValue = "true")
public class MapperSqlLoggingAutoConfiguration {

  private static final String PROPERTY_ENABLED =
      "open.vincentf13.mybatis.mapper-sql-logging.enabled";

  @Bean
  ApplicationRunner mapperSqlLoggingInitializer(
      ObjectProvider<SqlSessionFactory> sqlSessionFactoryProvider, LoggingSystem loggingSystem) {
    return args -> {
      Set<String> mapperPackages = new LinkedHashSet<>();
      sqlSessionFactoryProvider.stream()
          .forEach(
              factory ->
                  factory
                      .getConfiguration()
                      .getMapperRegistry()
                      .getMappers()
                      .forEach(
                          mapperClass -> {
                            String packageName = mapperClass.getPackageName();
                            if (StringUtils.hasText(packageName)) {
                              mapperPackages.add(packageName);
                            }
                          }));

      for (String packageName : mapperPackages) {
        loggingSystem.setLogLevel(packageName, LogLevel.DEBUG);
        OpenLog.debug(MysqlEvent.MAPPER_SQL_DEBUG_ENABLED, "package", packageName);
      }
    };
  }
}
