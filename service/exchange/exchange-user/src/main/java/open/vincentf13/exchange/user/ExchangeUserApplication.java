package open.vincentf13.exchange.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ExchangeUserApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExchangeUserApplication.class, args);
    }
}
