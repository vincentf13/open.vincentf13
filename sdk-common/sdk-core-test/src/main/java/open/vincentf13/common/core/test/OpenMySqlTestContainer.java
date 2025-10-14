package open.vincentf13.common.core.test;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.MySQLContainer;

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
        String schema = "test_" + UUID.randomUUID().toString().replace("-", "");

        try (var admin = DriverManager.getConnection(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword())) {
            admin.createStatement().execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        //        createSchema(schema);
        CURRENT_SCHEMA.set(schema);

        String jdbcUrl = MYSQL.getJdbcUrl().replace("databaseName=bootstrap", "databaseName=" + schema);
        registry.add("spring.datasource.url", () -> jdbcUrl);
        registry.add("spring.datasource.username", () -> "root");
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
        executeSqlInContainer("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4");
    }

    private static void dropSchema(String schema) {
        executeSqlInContainer("DROP DATABASE IF EXISTS `" + schema + "`");
    }

    private static void executeSqlInContainer(String sql) {
        try {
            var result = MYSQL.execInContainer("mysql", "-uroot", "-p" + MYSQL.getPassword(), "-e", sql);
            if (result.getExitCode() != 0) {
                throw new IllegalStateException("Command failed: " + sql + "\n" + result.getStderr());
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to execute SQL on MySQL container", ex);
        }
    }
}
