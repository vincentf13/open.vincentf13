package open.vincentf13.service.spot.ws;

import open.vincentf13.service.spot.infra.chronicle.Storage;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
    "open.vincentf13.service.spot.ws",
    "open.vincentf13.service.spot.infra"
})
public class WsApiApp {
    @jakarta.annotation.PostConstruct
    public void init() {
        open.vincentf13.service.spot.infra.metrics.GcMonitor.start(
            open.vincentf13.service.spot.infra.Constants.MetricsKey.GATEWAY_GC_COUNT,
            open.vincentf13.service.spot.infra.Constants.MetricsKey.GATEWAY_GC_LAST_INTERVAL_MS,
            open.vincentf13.service.spot.infra.Constants.MetricsKey.GATEWAY_GC_LAST_DURATION_MS,
            open.vincentf13.service.spot.infra.Constants.MetricsKey.GATEWAY_GC_HISTORY_START
        );
    }

    public static void main(String[] args) {
        SpringApplication.run(WsApiApp.class, args);
    }
}
