package test.open.vincentf13.common.core.test;


import open.vincentf13.common.core.test.OpenMySqlTestContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MySQL 容器整合測試
 *
 * 使用 dokcer 容器運行 MySql 測試的範例，
 * 各測試方法前後 動態建立 隨機Shema，各測試互相隔離，可使用平行測試，提升效能。
 */
@JdbcTest // 啟用 Spring JDBC 測試切片，只載入 DataSource / JdbcTemplate 相關 Bean
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE) // 使用自訂資料來源（Testcontainers），不要替換成內建資料庫
class MysqlTest {

    /**
     * @DynamicPropertySource 標註的 static 方法會在 Spring TestContext 建立 ApplicationContext 之前被呼叫。
     *
     * 內部每次會隨機建立一個 Shema，隔離 MySQL，使平行測試不互相影響。
     */
    @DynamicPropertySource
    static void registerMysqlProperties(DynamicPropertyRegistry registry) { // 將動態 schema 對應的 JDBC 連線資訊注入 Spring Environment
        OpenMySqlTestContainer.register(registry);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void createSchema() {
        OpenMySqlTestContainer.prepareSchema(dataSource);
        jdbcTemplate.execute("DROP TABLE IF EXISTS users");
        jdbcTemplate.execute(
                "CREATE TABLE users (id BIGINT PRIMARY KEY AUTO_INCREMENT, name VARCHAR(64) NOT NULL)");
    }

    @Test
    void insertAndFetchRecord() {
        jdbcTemplate.update("INSERT INTO users(name) VALUES (?)", "Vincent");
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM users WHERE name = ?", Integer.class, "Vincent");

        assertThat(count).isEqualTo(1);
    }


    @AfterEach
    void cleanupSchema() {
        OpenMySqlTestContainer.cleanupCurrentSchema(dataSource);
    }

}
