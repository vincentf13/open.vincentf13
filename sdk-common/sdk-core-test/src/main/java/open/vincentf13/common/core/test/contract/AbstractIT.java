package open.vincentf13.common.core.test.contract;

import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;

import java.util.stream.Stream;

/**
 * 容器化整合測試的基底類別。
 * 啟動 Spring 測試應用並連到 Testcontainers 啟動的臨時 MySQL、Kafka、Redis。
 */
@Testcontainers                                                      // 讓 JUnit 5 管理容器生命週期
@SpringBootTest(classes = AbstractIT.TestApplication.class)          // 啟動 Spring Boot 測試 Context
@ActiveProfiles("test")                                              // 使用 test Profile 組態
@TestInstance(TestInstance.Lifecycle.PER_METHOD)                     // 每個測試方法建立新測試實例（避免共享狀態）
public abstract class AbstractIT {

    // 注意：static final 讓「此基底類」與其所有子類共用同一組容器（同一 JVM 內）
    @Container
    protected static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>(DockerImageName.parse("mysql:8.4")) // 指定固定版本以避免 CI 漂移
                    .withUsername("test")                                 // 建立容器內帳號
                    .withPassword("test")                                 // 密碼
                    .withDatabaseName("app");                             // 預設資料庫名稱
    //              .withInitScript("sql/schema.sql");                    // 可選：啟動時執行建表/種子腳本

    @Container
    protected static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.2")) // 輕量快
                    .withExposedPorts(6379)                                 // 對外映射埠（隨機主機埠）
                    .waitingFor(Wait.forListeningPort());                   // 等待就緒後才開始測試


    @Container
    protected static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1")); // 單 Broker 測試用


    // 將容器連線資訊註冊到 Spring Environment，讓 DataSource/Kafka/Redis 指向這些臨時容器
    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        ensureContainersStarted();                                             // 並行啟動未運行的容器，加速整體啟動
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    // 以 Startables.deepStart 並行啟動容器；若已運行則略過
    private static void ensureContainersStarted() {
        Startables.deepStart(Stream.of(MYSQL, KAFKA, REDIS).filter(container -> !container.isRunning()))
                .join();
    }

    // 測試用最小 Spring Boot 應用；scanBasePackages 指向業務程式碼
    @SpringBootApplication(scanBasePackages = "open.vincentf13")
    static class TestApplication {
    }
}
