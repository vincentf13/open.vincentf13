package open.vincentf13.service.spot.matching;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {
    "open.vincentf13.service.spot.matching",
    "open.vincentf13.service.spot.infra"
})
public class MatchingApp {
    public static void main(String[] args) {
        System.setProperty("spring.main.web-application-type", "none");
        SpringApplication.run(MatchingApp.class, args);
    }
}
