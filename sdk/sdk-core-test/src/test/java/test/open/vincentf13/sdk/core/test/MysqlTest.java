package test.open.vincentf13.sdk.core.test;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import open.vincentf13.sdk.core.test.OpenMySqlTestContainer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * MySQL 容器整合測試
 *
 * <p>使用 dokcer mysql容器運行 MySql 測試 各測試方法前後 動態建立 隨機Shema，各測試互相隔離，可使用平行測試，提升效能。
 *
 * <p>若配置 open.vincentf13.sdk.core.test.testcontainer.mysql.enabled=false 則連到真實數據庫，不啟用 mysql 容器。
 *
 * <p>真實數據庫配置：spring.datasource.{url,username,password} 且該帳號具備 CREATE DATABASE / DROP DATABASE
 * 權限，才能順利建立／清除動態 schema。
 */
@JdbcTest // 啟用 Spring JDBC 測試切片，只載入 DataSource / JdbcTemplate 相關 Bean
@AutoConfigureTestDatabase(
    replace = AutoConfigureTestDatabase.Replace.NONE) // 使用自訂資料來源（Testcontainers），不要替換成內建資料庫
class MysqlTest {

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private DataSource dataSource;

  /**
   * @DynamicPropertySource 標註的 static 方法會在 Spring TestContext 建立 ApplicationContext 前呼叫。
   */
  @DynamicPropertySource
  static void registerMysqlProperties(DynamicPropertyRegistry registry) {
    OpenMySqlTestContainer.register(registry);
  }

  @AfterAll
  static void cleanupSchema() {
    OpenMySqlTestContainer.clearAdminConnection();
  }

  @BeforeEach
  void reflashTable() {
    // 動態建立隨機 schema
    OpenMySqlTestContainer.prepareSchema();
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
}
