package open.vincentf13.service.spot.matching;

import net.openhft.affinity.AffinityLock;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
    "open.vincentf13.service.spot.matching",
    "open.vincentf13.service.spot.infra"
})
public class MatchingApp {
    @jakarta.annotation.PostConstruct
    public void init() {
        open.vincentf13.service.spot.infra.metrics.GcMonitor.start(
            open.vincentf13.service.spot.infra.Constants.MetricsKey.MATCHING_GC_COUNT,
            open.vincentf13.service.spot.infra.Constants.MetricsKey.MATCHING_GC_LAST_INTERVAL_MS,
            open.vincentf13.service.spot.infra.Constants.MetricsKey.MATCHING_GC_LAST_DURATION_MS
        );
    }

    public static void main(String[] args) {
        SpringApplication.run(MatchingApp.class, args);
    }
}
