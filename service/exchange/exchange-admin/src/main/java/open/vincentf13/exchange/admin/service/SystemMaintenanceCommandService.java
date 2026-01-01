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
            "liquidation_queue"
    );

    private static final Set<String> PROTECTED_TOPICS = Set.of(
            "infra.connect.config",
            "infra.connect.offsets",
            "infra.connect.status",
            "cdc.infra-mysql-0"
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

        // 3. 等待異步清理完成 (Kafka 刪除 Topic 是異步的)
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 4. 觸發各服務重新載入快取或重置內存
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

        // Account
        try {
            accountMaintenanceClient.reloadCaches();
        } catch (Exception e) {
            System.err.println("Failed to trigger Account cache reload: " + e.getMessage());
        }

        // Order
        try {
            orderMaintenanceClient.reset();
        } catch (Exception e) {
            System.err.println("Failed to trigger Order cache reset: " + e.getMessage());
        }
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
