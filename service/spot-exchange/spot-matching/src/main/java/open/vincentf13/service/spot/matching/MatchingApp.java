package open.vincentf13.service.spot.matching;

import open.vincentf13.service.spot.matching.engine.OrderBook;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@SpringBootApplication
public class MatchingApp {
    public static void main(String[] args) {
        SpringApplication.run(MatchingApp.class, args);
    }

    @RestController
    public static class MetricsController {
        @GetMapping("/metrics/tps")
        public Map<String, Object> getTps() {
            return Map.of(
                "total_matches", OrderBook.TOTAL_MATCH_COUNT.get(),
                "timestamp", System.currentTimeMillis()
            );
        }
    }
}
