package open.vincentf13.sdk.spring.cloud.openfeign.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;

@AutoConfiguration
@EnableFeignClients(basePackages = "${open.vincentf13.cloud.feign.base-packages:open.vincentf13}")
public class OpenFeignClientsAutoConfiguration {}
