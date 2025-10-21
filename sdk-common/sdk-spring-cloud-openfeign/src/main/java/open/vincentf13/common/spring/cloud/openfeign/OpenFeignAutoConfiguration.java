package open.vincentf13.common.spring.cloud.openfeign;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.EnableFeignClients;
import open.vincentf13.common.core.OpenConstant;

@AutoConfiguration
@ConditionalOnClass(EnableFeignClients.class)
@ConditionalOnProperty(prefix = "spring.cloud.openfeign", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableFeignClients(basePackages = OpenConstant.BASE_PACKAGE)
public class OpenFeignAutoConfiguration {


}
