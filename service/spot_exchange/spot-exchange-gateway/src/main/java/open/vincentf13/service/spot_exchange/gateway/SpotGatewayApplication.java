package open.vincentf13.service.spot_exchange.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/** 
  現貨交易所 - 網關啟動類
  負責 WebSocket 接入與 Aeron 轉發
 */
@SpringBootApplication
@ComponentScan(basePackages = {
    "open.vincentf13.service.spot_exchange.gateway",
    "open.vincentf13.service.spot_exchange.infra",
    "open.vincentf13.service.spot_exchange.core" // 需要 StateStore 與 AeronConfig
})
public class SpotGatewayApplication {
    public static void main(String[] args) {
        System.setProperty("server.port", "8081");
        SpringApplication.run(SpotGatewayApplication.class, args);
    }
}
