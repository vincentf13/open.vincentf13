package open.vincentf13.service.spot.ws;

import open.vincentf13.service.spot.infra.chronicle.Storage;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
    "open.vincentf13.service.spot.ws",
    "open.vincentf13.service.spot.infra"
})
public class WsApiApp {
    public static void main(String[] args) {
        SpringApplication.run(WsApiApp.class, args);
    }
}
