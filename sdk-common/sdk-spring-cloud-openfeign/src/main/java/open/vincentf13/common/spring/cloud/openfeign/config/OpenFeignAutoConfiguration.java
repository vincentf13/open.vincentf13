package open.vincentf13.common.spring.cloud.openfeign.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.EnableFeignClients;

@AutoConfiguration
@ConditionalOnClass(EnableFeignClients.class)
@ConditionalOnProperty(prefix = "spring.cloud.openfeign", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableFeignClients(basePackages = OpenFeignAutoConfiguration.BASE_PACKAGES)
public class OpenFeignAutoConfiguration {

    static final String[] BASE_PACKAGES = new String[] {"open.vincentf13"};
}
