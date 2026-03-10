package open.vincentf13.service.spot.query.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {
    "open.vincentf13.service.spot.query",
    "open.vincentf13.service.spot.infra"
})
public class SpotQueryApplication {
    public static void main(String[] args) {
        System.setProperty("server.port", "8082");
        SpringApplication.run(SpotQueryApplication.class, args);
    }
}
