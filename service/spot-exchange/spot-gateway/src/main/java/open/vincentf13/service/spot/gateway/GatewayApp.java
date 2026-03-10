package open.vincentf13.service.spot.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {
    "open.vincentf13.service.spot.gateway",
    "open.vincentf13.service.spot.infra"
})
public class GatewayApp {
    public static void main(String[] args) {
        System.setProperty("server.port", "8081");
        SpringApplication.run(GatewayApp.class, args);
    }
}
