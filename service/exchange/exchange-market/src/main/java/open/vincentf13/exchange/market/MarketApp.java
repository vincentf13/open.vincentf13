package open.vincentf13.exchange.market;

import open.vincentf13.sdk.core.OpenConstant;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = OpenConstant.Package.Names.BASE_PACKAGE)
@EnableScheduling
public class MarketApp {

  public static void main(String[] args) {
    SpringApplication.run(MarketApp.class, args);
  }
}
