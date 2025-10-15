package test.open.vincentf13.common.core.test;


import open.vincentf13.common.core.test.OpenMySqlTestContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

// MySQL 容器整合測試：示範臨時資料庫 schema 操作與查詢
@JdbcTest // 啟用 Spring JDBC 測試切片，只載入 DataSource / JdbcTemplate 相關 Bean
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE) // 使用自訂資料來源（Testcontainers），不要替換成內建資料庫
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD) // 每個測試方法後銷毀 ApplicationContext，避免快取舊 schema 設定
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


    @BeforeEach
    void createSchema() {
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
        OpenMySqlTestContainer.cleanupCurrentSchema();
    }

}
