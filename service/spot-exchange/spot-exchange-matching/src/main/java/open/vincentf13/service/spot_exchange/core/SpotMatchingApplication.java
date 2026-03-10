package open.vincentf13.service.spot_exchange.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/** 
  現貨交易所 - 核心引擎啟動類
  負責 帳務風控、撮合計算 與 狀態持久化
 */
@SpringBootApplication
@ComponentScan(basePackages = {
    "open.vincentf13.service.spot_exchange.core",
    "open.vincentf13.service.spot_exchange.infra"
})
public class SpotMatchingApplication {
    public static void main(String[] args) {
        // 核心引擎通常不需要 Web 服務，關閉以節省資源
        System.setProperty("spring.main.web-application-type", "none");
        SpringApplication.run(SpotMatchingApplication.class, args);
    }
}
