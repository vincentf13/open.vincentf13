package open.vincentf13.service.spot.matching;

import open.vincentf13.service.spot.infra.chronicle.Storage;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
@ComponentScan(basePackages = {
    "open.vincentf13.service.spot.matching",
    "open.vincentf13.service.spot.infra"
})
public class MatchingApp {
    public static void main(String[] args) {
        // 啟動前強制觸發 Storage 初始化日誌
        Storage.self();
        SpringApplication.run(MatchingApp.class, args);
    }
}
