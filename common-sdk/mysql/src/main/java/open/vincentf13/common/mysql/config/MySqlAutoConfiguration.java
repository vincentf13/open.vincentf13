//package open.vincentf13.common.mysql.config;
//
//import javax.sql.DataSource;
//
//import org.apache.ibatis.session.Configuration;
//import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
//import org.springframework.boot.autoconfigure.AutoConfiguration;
//import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
//import org.springframework.context.annotation.Bean;
//
///**
// * Default MySQL auto-configuration helpers (MyBatis tweaks, etc.).
// */
//@AutoConfiguration
//public class MySqlAutoConfiguration {
//
//    @Bean
//    @ConditionalOnBean(DataSource.class)
//    public ConfigurationCustomizer defaultConfigurationCustomizer() {
//        return Configuration::setMapUnderscoreToCamelCase;
//    }
//}
