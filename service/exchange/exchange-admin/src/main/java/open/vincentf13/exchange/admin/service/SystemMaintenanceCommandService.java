package open.vincentf13.exchange.admin.service;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.admin.infra.client.ExchangeMarketMaintenanceClient;
import open.vincentf13.exchange.admin.infra.client.ExchangeMatchingMaintenanceClient;
import open.vincentf13.exchange.admin.infra.client.ExchangePositionMaintenanceClient;
import open.vincentf13.exchange.admin.infra.client.ExchangeRiskMaintenanceClient;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DeleteTopicsResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
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
            "mq_outbox",
            "mq_dead_letters",
            "liquidation_queue"
    );

    /**
     * 重置系統數據
     */
    @Transactional(rollbackFor = Exception.class)
    public void resetData() {
        // 1. 清理 Kafka Topics
        clearKafkaTopics();

        // 2. 清理資料庫表
        clearDatabaseTables();

        // 3. 觸發各服務重新載入快取或重置內存
        resetServiceCaches();
    }

    private void resetServiceCaches() {
        // Risk
        try {
            riskMaintenanceClient.reloadCaches();
        } catch (Exception e) {
            System.err.println("Failed to trigger Risk cache reload: " + e.getMessage());
        }

        // Position
        try {
            positionMaintenanceClient.reloadCaches();
        } catch (Exception e) {
            System.err.println("Failed to trigger Position cache reload: " + e.getMessage());
        }

        // Matching
        try {
            matchingMaintenanceClient.reset();
        } catch (Exception e) {
            System.err.println("Failed to trigger Matching engine reset: " + e.getMessage());
        }

        // Market
        try {
            marketMaintenanceClient.reset();
        } catch (Exception e) {
            System.err.println("Failed to trigger Market cache reset: " + e.getMessage());
        }
    }

    private void clearKafkaTopics() {
        try (AdminClient client = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            Set<String> topics = client.listTopics().names().get(10, TimeUnit.SECONDS);
            topics.removeIf(name -> name.startsWith("_") || name.startsWith("__"));
            
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
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        try {
            for (String table : TABLES_TO_CLEAR) {
                System.out.println("Clearing table: " + table);
                jdbcTemplate.execute("TRUNCATE TABLE " + table);
            }
        } finally {
            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
        }
    }
}