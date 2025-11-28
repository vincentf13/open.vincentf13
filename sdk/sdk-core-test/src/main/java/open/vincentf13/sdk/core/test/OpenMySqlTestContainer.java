package open.vincentf13.sdk.core.test;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.UUID;

/*
  靜態 MySQL Testcontainer 工具：集中管理容器啟動與屬性註冊，並為每個測試方法建立隔離的 schema。
 */
public final class OpenMySqlTestContainer {

    private static final ToggleableMySqlContainer MYSQL =
            new ToggleableMySqlContainer()
                    .withUsername("root")
                    .withPassword("test")
                    .withDatabaseName("test");

    private static final ThreadLocal<String> CURRENT_SCHEMA = new ThreadLocal<>();
    private static final ThreadLocal<Connection> ADMIN_CONNECTION = new ThreadLocal<>();

    private OpenMySqlTestContainer() {
    }

    public static void register(DynamicPropertyRegistry registry) {
        if (!TestContainerSettings.mysqlEnabled()) {
            return;
        }
        // 只會在第一次需要時啟動容器。整個 JVM 的測試流程中都共用這顆容器，不會因為測試方法或類別被反覆重啟。
        MYSQL.start();
        registry.add("spring.datasource.url", () -> buildJdbcUrl(MYSQL.getDatabaseName()));
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "1");
        registry.add("spring.datasource.hikari.minimum-idle", () -> "1");
    }



    /*
  為即將執行的測試方法建立隔離 schema，並將資料來源切換到該 schema。
 */
    public static String prepareSchema() {
        String schema = "test_" + UUID.randomUUID().toString().replace('-', '_');
        createSchema(schema);
        CURRENT_SCHEMA.set(schema);
        switchSchema(schema);
        return schema;
    }


    private static void createSchema(String schema) {
        execute("CREATE DATABASE IF NOT EXISTS `" + schema + "`");
    }


    private static void switchSchema(String schema) {
        execute("USE " + schema);
    }

    private static void execute(String sql) {
        try (Statement statement = adminConnection().createStatement()) {
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

    private static Connection adminConnection() {
        Connection connection = ADMIN_CONNECTION.get();
        if (connection == null || isConnectionInvalid(connection)) {
            connection = createAdminConnection();
            ADMIN_CONNECTION.set(connection);
        }
        return connection;
    }

    private static Connection createAdminConnection() {
        try {
            if (!TestContainerSettings.mysqlEnabled()) {
                return realDatabaseConnection();
            }
            Connection connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), rootUser(), rootPassword());
            connection.setAutoCommit(true);
            return connection;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to obtain admin connection for MySQL test container", ex);
        }
    }

    private static Connection realDatabaseConnection() throws SQLException {
        String url = resolveConfig("spring.datasource.url");
        if (isBlank(url)) {
            throw new IllegalStateException("spring.datasource.url is required when MySQL testcontainer is disabled");
        }
        String username = resolveConfig("spring.datasource.username");
        if (isBlank(username)) {
            username = "root";
        }
        String password = resolveConfig("spring.datasource.password");
        Connection connection = DriverManager.getConnection(url, username, password);
        connection.setAutoCommit(true);
        return connection;
    }

    private static boolean isConnectionInvalid(Connection connection) {
        try {
            return connection.isClosed() || !connection.isValid(1);
        } catch (SQLException ex) {
            return true;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String resolveConfig(String key) {
        String value = System.getProperty(key);
        if (!isBlank(value)) {
            return value;
        }
        String envKey = key.toUpperCase(Locale.ROOT).replace('.', '_');
        value = System.getenv(envKey);
        return isBlank(value) ? null : value;
    }

    public static void clearAdminConnection() {
        Connection connection = ADMIN_CONNECTION.get();
        if (connection != null) {
            try {
                if (!connection.isClosed()) {
                    connection.close();
                }
            } catch (SQLException ignored) {
            }
            ADMIN_CONNECTION.remove();
        }
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
