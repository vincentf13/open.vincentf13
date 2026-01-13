package open.vincentf13.sdk.spring.cloud.openfeign.exception;

import feign.FeignException;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@ConditionalOnClass({FeignException.class, EnableFeignClients.class})
@ConditionalOnProperty(
    prefix = "spring.cloud.openfeign",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@Import(OpenFeignExceptionAdvice.class)
public class OpenFeignExceptionAdviceConfig {}
