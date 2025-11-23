package open.vincentf13.exchange.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import open.vincentf13.sdk.core.OpenConstant;

@SpringBootApplication(scanBasePackages = OpenConstant.BASE_PACKAGE)
public class GatewayApp {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApp.class, args);
    }
}
