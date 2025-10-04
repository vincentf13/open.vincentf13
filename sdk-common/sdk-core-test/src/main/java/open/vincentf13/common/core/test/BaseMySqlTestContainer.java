package open.vincentf13.common.core.test;

import org.junit.jupiter.api.TestInstance;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 容器化整合測試的共用基底：啟動並共用 Testcontainers 的 MySQL/Kafka/Redis 與對應連線屬性。
 */
@Testcontainers                                                     // 讓 JUnit 5 管理容器生命週期
@TestInstance(TestInstance.Lifecycle.PER_METHOD)                     // 每個測試方法建立新測試實例（避免共享狀態）
public abstract class BaseMySqlTestContainer {

    // 注意：static final 讓「此基底類」與其所有子類共用同一組容器（同一 JVM 內）
    @Container
    protected static final ToggleableMySqlContainer MYSQL =
            new ToggleableMySqlContainer()
                    .withUsername("test")
                    .withPassword("test")
                    .withDatabaseName("app");

    // 將容器連線資訊註冊到 Spring Environment，讓 DataSource/Kafka/Redis 指向這些臨時容器
    @DynamicPropertySource
    public static void registerProps(DynamicPropertyRegistry registry) {
        if (!TestContainerSettings.mysqlEnabled()) {
            return;
        }

        MYSQL.start();
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
//        String db = "app_" + UUID.randomUUID();
//        registry.add("spring.datasource.url",                            // 每個測試使用獨立 schema
//                () -> MYSQL.getJdbcUrl().replace("/test", "/" + db));
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    }

    private static class ToggleableMySqlContainer extends MySQLContainer<ToggleableMySqlContainer> {

        private ToggleableMySqlContainer() {
            super(DockerImageName.parse("mysql:8.4"));
        }

        @Override
        public void start() {
            if (!TestContainerSettings.mysqlEnabled() || isRunning()) {
                return;
            }
            super.start();
        }

        @Override
        public void stop() {
            if (!TestContainerSettings.mysqlEnabled()) {
                return;
            }
            super.stop();
        }
    }
}
