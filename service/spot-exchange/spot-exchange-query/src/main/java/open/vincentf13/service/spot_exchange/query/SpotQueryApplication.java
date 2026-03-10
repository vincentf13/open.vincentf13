package open.vincentf13.service.spot_exchange.query;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {
    "open.vincentf13.service.spot_exchange.query",
    "open.vincentf13.service.spot_exchange.infra"
})
public class SpotQueryApplication {
    public static void main(String[] args) {
        System.setProperty("server.port", "8082");
        SpringApplication.run(SpotQueryApplication.class, args);
    }
}
