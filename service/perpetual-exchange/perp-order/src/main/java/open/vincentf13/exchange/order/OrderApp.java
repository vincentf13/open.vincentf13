package open.vincentf13.exchange.order;

import open.vincentf13.sdk.core.OpenConstant;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication(scanBasePackages = OpenConstant.Package.Names.BASE_PACKAGE)
public class OrderApp {
    
    public static void main(String[] args) {
        SpringApplication.run(OrderApp.class, args);
    }
}
