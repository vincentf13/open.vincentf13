package open.vincentf13.common.core.test;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 靜態 MySQL Testcontainer 工具，集中管理容器啟動與屬性註冊。
 */
public final class OpenMySqlTestContainer {

    private static final ToggleableMySqlContainer MYSQL =
            new ToggleableMySqlContainer()
                    .withUsername("test")
                    .withPassword("test")
                    .withDatabaseName("app");

    private OpenMySqlTestContainer() {
    }

    public static void register(DynamicPropertyRegistry registry) {
        if (!TestContainerSettings.mysqlEnabled()) {
            return;
        }
        MYSQL.start();
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    }

    public static MySQLContainer<?> container() {
        return MYSQL;
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
