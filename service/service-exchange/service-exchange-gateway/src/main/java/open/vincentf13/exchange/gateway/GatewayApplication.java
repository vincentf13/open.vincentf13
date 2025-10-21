package open.vincentf13.exchange.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import open.vincentf13.common.core.OpenConstant;

@SpringBootApplication(scanBasePackages = OpenConstant.BASE_PACKAGE)
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
