package open.vincentf13.exchange.order;

import open.vincentf13.exchange.order.config.OrderEventTopicsProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(OrderEventTopicsProperties.class)
@ConfigurationPropertiesScan(basePackageClasses = OrderEventTopicsProperties.class)
public class OrderApp {

    public static void main(String[] args) {
        SpringApplication.run(OrderApp.class, args);
    }
}
