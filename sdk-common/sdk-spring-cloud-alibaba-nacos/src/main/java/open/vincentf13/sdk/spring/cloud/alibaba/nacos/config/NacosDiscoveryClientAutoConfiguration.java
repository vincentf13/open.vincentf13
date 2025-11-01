package open.vincentf13.sdk.spring.cloud.alibaba.nacos.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@AutoConfiguration
@ConditionalOnClass(EnableDiscoveryClient.class)
@EnableDiscoveryClient
public class NacosDiscoveryClientAutoConfiguration {
}
