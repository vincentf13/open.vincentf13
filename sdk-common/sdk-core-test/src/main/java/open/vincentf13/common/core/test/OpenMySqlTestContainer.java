package open.vincentf13.common.core.test;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

/**
 * 靜態 MySQL Testcontainer 工具：集中管理容器啟動與屬性註冊，並為每個測試方法建立隔離的 schema。
 */
public final class OpenMySqlTestContainer {

    private static final ToggleableMySqlContainer MYSQL =
            new ToggleableMySqlContainer()
                    .withUsername("root")
                    .withPassword("test")
                    .withDatabaseName("test");

    private static final ThreadLocal<String> CURRENT_SCHEMA = new ThreadLocal<>();

    private OpenMySqlTestContainer() {
    }

    public static void register(DynamicPropertyRegistry registry) {
        if (!TestContainerSettings.mysqlEnabled()) {
            return;
        }
        MYSQL.start();
        registry.add("spring.datasource.url", () -> buildJdbcUrl(MYSQL.getDatabaseName()));
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "1");
        registry.add("spring.datasource.hikari.minimum-idle", () -> "1");
    }

    public static MySQLContainer<?> container() {
        return MYSQL;
    }

    public static String currentSchema() {
        return CURRENT_SCHEMA.get();
    }

    /**
     * 為即將執行的測試方法建立隔離 schema，並將資料來源切換到該 schema。
     */
    public static String prepareSchema(DataSource dataSource) {
        if (!TestContainerSettings.mysqlEnabled()) {
            CURRENT_SCHEMA.remove();
            return null;
        }
        String schema = "test_" + UUID.randomUUID().toString().replace('-', '_');
        createSchema(schema);
        CURRENT_SCHEMA.set(schema);
        switchSchema(schema);
        return schema;
    }

    /**
     * 測試方法完成後清理 schema，並恢復資料來源預設 schema。
     */
    public static void cleanupCurrentSchema(DataSource dataSource) {
        if (!TestContainerSettings.mysqlEnabled()) {
            CURRENT_SCHEMA.remove();
            return;
        }
        switchSchema(MYSQL.getDatabaseName());
        String schema = CURRENT_SCHEMA.get();
        if (schema != null) {
            dropSchema(schema);
        }
        CURRENT_SCHEMA.remove();
    }

    private static void createSchema(String schema) {
        execute("CREATE DATABASE IF NOT EXISTS `" + schema + "`");
    }

    private static void dropSchema(String schema) {
        execute("DROP DATABASE IF EXISTS `" + schema + "`");
    }

    private static void switchSchema(String schema) {
        execute("USE " + schema);
    }

    private static void execute(String sql) {
        try (Connection connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), rootUser(), rootPassword());
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to execute SQL on MySQL test container", ex);
        }
    }

    private static String buildJdbcUrl(String schema) {
        String original = MYSQL.getJdbcUrl();
        int lastSlash = original.lastIndexOf('/');
        if (lastSlash < 0) {
            return original;
        }
        int queryIndex = original.indexOf('?', lastSlash);
        if (queryIndex == -1) {
            return original.substring(0, lastSlash + 1) + schema;
        }
        return original.substring(0, lastSlash + 1) + schema + original.substring(queryIndex);
    }

    private static String rootUser() {
        return "root";
    }

    private static String rootPassword() {
        return MYSQL.getPassword();
    }

    private static final class ToggleableMySqlContainer extends MySQLContainer<ToggleableMySqlContainer> {

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
