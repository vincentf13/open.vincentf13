package open.vincentf13.service.spot_exchange.matching.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/** 
  現貨交易所 - 撮合核心啟動類
 */
@SpringBootApplication
@ComponentScan(basePackages = {
    "open.vincentf13.service.spot_exchange.matching",
    "open.vincentf13.service.spot_exchange.infra"
})
public class SpotMatchingApplication {
    public static void main(String[] args) {
        System.setProperty("spring.main.web-application-type", "none");
        SpringApplication.run(SpotMatchingApplication.class, args);
    }
}
