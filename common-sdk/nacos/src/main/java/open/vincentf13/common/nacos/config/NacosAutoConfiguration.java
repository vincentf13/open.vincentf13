package open.vincentf13.common.nacos.config;

import com.alibaba.cloud.nacos.NacosConfigManager;
import com.alibaba.cloud.nacos.refresh.NacosContextRefresher;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Convenience auto-configuration for Nacos config refresh support.
 */
@AutoConfiguration
public class NacosAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(NacosConfigManager.class)
    public NacosContextRefresher nacosContextRefresher(NacosConfigManager nacosConfigManager) {
        return new NacosContextRefresher(nacosConfigManager);
    }
}
