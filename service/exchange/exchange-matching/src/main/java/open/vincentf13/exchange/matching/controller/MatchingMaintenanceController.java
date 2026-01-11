package open.vincentf13.exchange.matching.controller;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.matching.infra.bootstrap.StartupCacheLoader;
import open.vincentf13.exchange.matching.infra.cache.InstrumentCache;
import open.vincentf13.exchange.matching.infra.loader.WalLoader;
import open.vincentf13.exchange.matching.service.MatchingEngine;
import open.vincentf13.exchange.matching.sdk.rest.api.MatchingMaintenanceApi;
import open.vincentf13.sdk.infra.kafka.consumer.reset.KafkaConsumerResetService;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/matching/maintenance")
public class MatchingMaintenanceController implements MatchingMaintenanceApi {

    private final MatchingEngine matchingEngine;
    private final WalLoader walLoader;
    private final InstrumentCache instrumentCache;
    private final StartupCacheLoader startupCacheLoader;
    private final KafkaConsumerResetService kafkaConsumerResetService;

    @Override
    public OpenApiResponse<Void> reset() {
        // 1. 重置組件狀態
        matchingEngine.reset();
        walLoader.reset();
        instrumentCache.clear();

        // 2. 刪除資料目錄
        clearMatchingDataDirectory();

        // 3. 重新載入初始快取
        startupCacheLoader.loadCaches();

        kafkaConsumerResetService.resetConsumers();
        return OpenApiResponse.success(null);
    }

    private void clearMatchingDataDirectory() {
        Path dataDir = Path.of("data/matching");
        if (!Files.exists(dataDir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dataDir)) {
            walk.sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        if (!path.equals(dataDir)) { // 保留目錄本身，僅清空內容
                            Files.delete(path);
                        }
                    } catch (IOException e) {
                        System.err.println("Failed to delete path: " + path + " - " + e.getMessage());
                    }
                });
        } catch (IOException e) {
            System.err.println("Failed to walk data directory: " + e.getMessage());
        }
    }
}
