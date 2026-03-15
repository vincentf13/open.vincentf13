package open.vincentf13.service.spot.matching;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "open.vincentf13.service.spot")
public class MatchingApp {
    public static void main(String[] args) {
        SpringApplication.run(MatchingApp.class, args);
    }
}
