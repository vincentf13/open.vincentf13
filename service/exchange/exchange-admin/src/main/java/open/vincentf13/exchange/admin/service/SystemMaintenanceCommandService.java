package open.vincentf13.exchange.admin.service;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.admin.infra.client.ExchangeRiskMaintenanceClient;
import open.vincentf13.sdk.core.log.OpenLog;
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

        // 3. 觸發 Risk 服務重新載入快取 (確保其讀取到清空後的狀態)
        try {
            riskMaintenanceClient.reloadCaches();
        } catch (Exception e) {
            System.err.println("Failed to trigger Risk cache reload: " + e.getMessage());
        }
    }

    private void clearKafkaTopics() {
        try (AdminClient client = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            Set<String> topics = client.listTopics().names().get(10, TimeUnit.SECONDS);
            // 排除系統 Topic
            topics.removeIf(name -> name.startsWith("_") || name.startsWith("__"));
            
            if (!topics.isEmpty()) {
                System.out.println("Deleting Kafka topics: " + topics);
                DeleteTopicsResult result = client.deleteTopics(topics);
                result.all().get(30, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            System.err.println("Failed to clear Kafka topics: " + e.getMessage());
            // Kafka 清理失敗不影響資料庫事務回滾，僅記錄
        }
    }

    private void clearDatabaseTables() {
        // 關閉外鍵檢查以便清空所有表
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
