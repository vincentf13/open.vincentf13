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
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class MysqlTest {

    @DynamicPropertySource
    static void registerMysqlProperties(DynamicPropertyRegistry registry) {
        OpenMySqlTestContainer.register(registry);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void createSchema() {

//        jdbcTemplate.execute("GRANT ALL ON `MYSQL_DATABASE`.* TO 'MYSQL_USER'@'%'");
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
