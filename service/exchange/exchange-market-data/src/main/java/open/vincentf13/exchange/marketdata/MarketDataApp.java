package open.vincentf13.exchange.marketdata;

import open.vincentf13.sdk.core.OpenConstant;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = OpenConstant.BASE_PACKAGE)
@EnableScheduling
public class MarketDataApp {

    public static void main(String[] args) {
        SpringApplication.run(MarketDataApp.class, args);
    }
}
