package open.vincentf13.exchange.admin.service;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.sdk.rest.client.ExchangeAccountMaintenanceClient;
import open.vincentf13.exchange.market.sdk.rest.client.ExchangeMarketMaintenanceClient;
import open.vincentf13.exchange.matching.sdk.rest.client.ExchangeMatchingMaintenanceClient;
import open.vincentf13.exchange.order.sdk.rest.client.ExchangeOrderMaintenanceClient;
import open.vincentf13.exchange.position.sdk.rest.client.ExchangePositionMaintenanceClient;
import open.vincentf13.exchange.risk.sdk.rest.client.ExchangeRiskMaintenanceClient;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DeleteTopicsResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class SystemMaintenanceCommandService {

    private final KafkaAdmin kafkaAdmin;
    private final JdbcTemplate jdbcTemplate;
    private final ExchangeRiskMaintenanceClient riskMaintenanceClient;
    private final ExchangePositionMaintenanceClient positionMaintenanceClient;
    private final ExchangeMatchingMaintenanceClient matchingMaintenanceClient;
    private final ExchangeMarketMaintenanceClient marketMaintenanceClient;
    private final ExchangeAccountMaintenanceClient accountMaintenanceClient;
    private final ExchangeOrderMaintenanceClient orderMaintenanceClient;

    private static final List<String> TABLES_TO_CLEAR = List.of(
            "positions",
            "platform_accounts",
            "user_journal",
            "user_accounts",
            "sys_pending_tasks",
            "platform_journal",
            "order_events",
            "position_events",
            "trade",
            "kline_buckets",
            "mark_price_snapshots",
            "orders",
            "risk_snapshots",
            "mq_dead_letters",
            "liquidation_queue",
            "mq_outbox"
    );

    private static final Set<String> PROTECTED_TOPICS = Set.of(
            "infra.connect.config",
            "infra.connect.offsets",
            "infra.connect.status",
            "infra-mysql-0",
            "cdc.infra-mysql-0"
    );

    /**
     * 重置系統數據
     */
    public void resetData() {
        // 1. 併發執行：清理 Kafka Topics 與 清理資料庫表
        CompletableFuture<Void> kafkaTask = CompletableFuture.runAsync(this::clearKafkaTopics);
        CompletableFuture<Void> dbTask = CompletableFuture.runAsync(this::clearDatabaseTables);

        // 等待這兩個核心清理任務完成
        CompletableFuture.allOf(kafkaTask, dbTask).join();

        // 2. 等待異步清理後續效應 (Kafka 刪除 Topic 是異步的，給予緩衝)
        try {
            TimeUnit.SECONDS.sleep(2);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 3. 併發觸發各服務重新載入快取或重置內存
        resetServiceCaches();
    }

    private void resetServiceCaches() {
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        // Risk
        futures.add(CompletableFuture.runAsync(() -> {
            try { riskMaintenanceClient.reloadCaches(); } catch (Exception e) {
                System.err.println("Failed to trigger Risk cache reload: " + e.getMessage());
            }
        }));

        // Position
        futures.add(CompletableFuture.runAsync(() -> {
            try { positionMaintenanceClient.reloadCaches(); } catch (Exception e) {
                System.err.println("Failed to trigger Position cache reload: " + e.getMessage());
            }
        }));

        // Matching
        futures.add(CompletableFuture.runAsync(() -> {
            try { matchingMaintenanceClient.reset(); } catch (Exception e) {
                System.err.println("Failed to trigger Matching engine reset: " + e.getMessage());
            }
        }));

        // Market
        futures.add(CompletableFuture.runAsync(() -> {
            try { marketMaintenanceClient.reset(); } catch (Exception e) {
                System.err.println("Failed to trigger Market cache reset: " + e.getMessage());
            }
        }));

        // Account
        futures.add(CompletableFuture.runAsync(() -> {
            try { accountMaintenanceClient.reloadCaches(); } catch (Exception e) {
                System.err.println("Failed to trigger Account cache reload: " + e.getMessage());
            }
        }));

        // Order
        futures.add(CompletableFuture.runAsync(() -> {
            try { orderMaintenanceClient.reset(); } catch (Exception e) {
                System.err.println("Failed to trigger Order cache reset: " + e.getMessage());
            }
        }));

        // 等待所有服務重置請求發出完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    private void clearKafkaTopics() {
        try (AdminClient client = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            Set<String> topics = client.listTopics().names().get(10, TimeUnit.SECONDS);
            topics.removeIf(name -> name.startsWith("_") || name.startsWith("__") || PROTECTED_TOPICS.contains(name));
            
            if (!topics.isEmpty()) {
                System.out.println("Deleting Kafka topics: " + topics);
                DeleteTopicsResult result = client.deleteTopics(topics);
                result.all().get(30, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            System.err.println("Failed to clear Kafka topics: " + e.getMessage());
        }
    }

    private void clearDatabaseTables() {
        List<String> sqls = new ArrayList<>();
        sqls.add("SET FOREIGN_KEY_CHECKS = 0");
        for (String table : TABLES_TO_CLEAR) {
            if ("mq_outbox".equals(table)) {
                sqls.add("DELETE FROM " + table);
            } else {
                sqls.add("TRUNCATE TABLE " + table);
            }
        }
        sqls.add("SET FOREIGN_KEY_CHECKS = 1");
        
        System.out.println("Executing batch DB clear...");
        jdbcTemplate.batchUpdate(sqls.toArray(new String[0]));
    }
}
