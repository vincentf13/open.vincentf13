package open.vincentf13.service.spot_exchange.gateway.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {
    "open.vincentf13.service.spot_exchange.gateway",
    "open.vincentf13.service.spot_exchange.infra"
})
public class SpotGatewayApplication {
    public static void main(String[] args) {
        System.setProperty("server.port", "8081");
        SpringApplication.run(SpotGatewayApplication.class, args);
    }
}
