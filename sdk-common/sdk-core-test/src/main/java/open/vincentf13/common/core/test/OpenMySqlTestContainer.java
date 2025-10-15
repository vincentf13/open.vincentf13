package open.vincentf13.common.core.test;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.MySQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

/**
 * 靜態 MySQL Testcontainer 工具：為每個測試方法建立獨立 schema，並於測試後清理。
 */
public final class OpenMySqlTestContainer {

    private static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4")
                    .withUsername("root")
                    .withPassword("test")
                    .withDatabaseName("bootstrap");

    private static final ThreadLocal<String> CURRENT_SCHEMA = new ThreadLocal<>();

    private OpenMySqlTestContainer() {
    }

    public static void register(DynamicPropertyRegistry registry) {
        if (!TestContainerSettings.mysqlEnabled()) {
            return;
        }
        MYSQL.start();

        String schema = "test_" + UUID.randomUUID().toString().replace('-', '_');
        createSchema(schema);
        CURRENT_SCHEMA.set(schema);

        registry.add("spring.datasource.url", () -> buildJdbcUrl(schema));
        registry.add("spring.datasource.username", () -> MYSQL.getUsername());
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    }

    public static void cleanupCurrentSchema() {
        String schema = CURRENT_SCHEMA.get();
        if (schema == null) {
            return;
        }
        dropSchema(schema);
        CURRENT_SCHEMA.remove();
    }

    private static void createSchema(String schema) {
        execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4");
    }

    private static void dropSchema(String schema) {
        execute("DROP DATABASE IF EXISTS `" + schema + "`");
    }

    private static void execute(String sql) {
        try (Connection connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
            connection.createStatement().execute(sql);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to execute SQL on MySQL test container", ex);
        }
    }

    private static String buildJdbcUrl(String schema) {
        return MYSQL.getJdbcUrl().replace("databaseName=" + MYSQL.getDatabaseName(), "databaseName=" + schema);
    }
}
