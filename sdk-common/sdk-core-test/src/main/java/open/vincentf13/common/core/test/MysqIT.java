package open.vincentf13.common.core.test;

import static org.assertj.core.api.Assertions.assertThat;

import open.vincentf13.common.core.test.contract.AbstractIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

// MySQL 容器整合測試：示範臨時資料庫 schema 操作與查詢
class MysqIT extends AbstractIT {

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void createSchema() {
    // 每次重建資料表確保測試獨立
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
