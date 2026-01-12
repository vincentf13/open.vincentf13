package open.vincentf13.exchange.admin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.sdk.rest.client.ExchangeAccountMaintenanceClient;
import open.vincentf13.exchange.admin.infra.AdminEvent;
import open.vincentf13.exchange.market.sdk.rest.client.ExchangeMarketMaintenanceClient;
import open.vincentf13.exchange.matching.sdk.rest.client.ExchangeMatchingMaintenanceClient;
import open.vincentf13.exchange.order.sdk.rest.client.ExchangeOrderMaintenanceClient;
import open.vincentf13.exchange.position.sdk.rest.client.ExchangePositionMaintenanceClient;
import open.vincentf13.exchange.risk.sdk.rest.client.ExchangeRiskMaintenanceClient;
import open.vincentf13.sdk.core.log.OpenLog;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DeleteTopicsResult;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

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
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String KAFKA_CONNECT_URL = "http://infra-kafka-connect.default.svc.cluster.local:8083";
    private static final String CONNECTOR_NAME = "mysql-cdc";

    // ... (existing TABLES_TO_CLEAR and PROTECTED_TOPICS) ...

    private static final List<String> TABLES_TO_CLEAR = List.of(
            "positions",
            "platform_accounts",
            "user_journal",
            "user_accounts",
            "retry_task",
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

        // 4. 檢查並重置 Kafka Connector
        checkAndResetKafkaConnector();
    }

    private void checkAndResetKafkaConnector() {
        System.out.println("Checking Kafka Connector status...");
        try {
            String statusUrl = KAFKA_CONNECT_URL + "/connectors/" + CONNECTOR_NAME + "/status";
            ResponseEntity<String> response = restTemplate.getForEntity(statusUrl, String.class);
            
            boolean needReset = false;
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode tasks = root.path("tasks");
                if (tasks.isArray() && tasks.size() > 0) {
                    String state = tasks.get(0).path("state").asText();
                    if (!"RUNNING".equalsIgnoreCase(state)) {
                        System.out.println("Connector task state is " + state + ", needs reset.");
                        needReset = true;
                    } else {
                        System.out.println("Connector task state is RUNNING.");
                    }
                } else {
                    System.out.println("Connector has no tasks, needs reset.");
                    needReset = true;
                }
            } else {
                System.out.println("Connector status check failed or not found, needs reset.");
                needReset = true;
            }

            if (needReset) {
                resetKafkaConnector();
            }

        } catch (Exception e) {
            System.err.println("Error checking connector status (might not exist): " + e.getMessage());
            // If check fails (e.g. 404), assume it needs creation/reset
            resetKafkaConnector();
        }
    }

    private void resetKafkaConnector() {
        System.out.println("Resetting Kafka Connector...");
        try {
            // 1. Delete existing connector
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.setAccept(List.of(MediaType.APPLICATION_JSON));
                HttpEntity<Void> entity = new HttpEntity<>(headers);
                
                restTemplate.exchange(
                        KAFKA_CONNECT_URL + "/connectors/" + CONNECTOR_NAME,
                        HttpMethod.DELETE,
                        entity,
                        String.class
                );
                System.out.println("Deleted existing connector.");
                // Wait for deletion to propagate
                TimeUnit.SECONDS.sleep(5); 
            } catch (Exception e) {
                OpenLog.warn(AdminEvent.KAFKA_CONNECTOR_DELETE_FAILED, e, "connector", CONNECTOR_NAME);
            }

            // 2. Create new connector
            String createUrl = KAFKA_CONNECT_URL + "/connectors";
            // reuse headers or create new ones
            HttpHeaders createHeaders = new HttpHeaders();
            createHeaders.setContentType(MediaType.APPLICATION_JSON);

            String payload = """
                {
                  "name": "mysql-cdc",
                  "config": {
                    "connector.class": "io.debezium.connector.mysql.MySqlConnector",
                    "database.hostname": "infra-mysql-0.infra-mysql-headless.default.svc.cluster.local",
                    "database.port": "3306",
                    "database.user": "root",
                    "database.password": "root",
                    "database.server.id": "5401",
                    "topic.prefix": "infra-mysql-0",
                    "database.include.list": "exchange",
                    "table.include.list": "exchange.mq_outbox",
                    "snapshot.mode": "initial",
                    "schema.history.internal.kafka.bootstrap.servers": "infra-kafka.default.svc.cluster.local:9092",
                    "schema.history.internal.kafka.topic": "cdc.infra-mysql-0",
                    "schema.history.internal.kafka.replication.factor": "3",
                    "tombstones.on.delete": "false",
                    "transforms": "mq_outbox",
                    "transforms.mq_outbox.type": "io.debezium.transforms.outbox.EventRouter",
                    "transforms.mq_outbox.route.by.field": "aggregate_type",
                
                    "transforms.mq_outbox.route.topic.replacement": "${routedByValue}",
                    "transforms.mq_outbox.table.field.event.id": "event_id",
                    "transforms.mq_outbox.table.field.event.key": "aggregate_id",
                    "transforms.mq_outbox.table.field.event.type": "event_type",
                    "transforms.mq_outbox.table.field.payload": "payload",
                    "transforms.mq_outbox.table.fields.additional.placement": "headers:header:all",
                    "poll.interval.ms": "50",
                    "producer.override.linger.ms": "0"
                  }
                }
                """;

            HttpEntity<String> request = new HttpEntity<>(payload, createHeaders);
            ResponseEntity<String> createResponse = restTemplate.postForEntity(createUrl, request, String.class);
            
            if (createResponse.getStatusCode().is2xxSuccessful()) {
                System.out.println("Successfully created Kafka Connector.");
            } else {
                System.err.println("Failed to create Kafka Connector: " + createResponse.getStatusCode());
            }

        } catch (Exception e) {
            System.err.println("Failed to reset Kafka Connector: " + e.getMessage());
        }
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
