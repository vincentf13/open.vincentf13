package open.vincentf13.service.spot.ws;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication(scanBasePackages = {
    "open.vincentf13.service.spot.ws",
    "open.vincentf13.service.spot.infra"
})
public class WsApiApp {
    public static void main(String[] args) {
        SpringApplication.run(WsApiApp.class, args);
    }
}
