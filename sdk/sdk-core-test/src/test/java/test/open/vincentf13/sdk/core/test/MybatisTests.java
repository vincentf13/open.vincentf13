package test.open.vincentf13.sdk.core.test;

import static org.assertj.core.api.Assertions.assertThat;

import open.vincentf13.sdk.core.test.OpenMySqlTestContainer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import test.open.vincentf13.sdk.core.test.Sample.MybatisUser;
import test.open.vincentf13.sdk.core.test.Sample.MybatisUserMapper;

/**
 * MyBatis 切片測試：透過 Mapper 驗證 MySQL 臨時資料庫的增刪查
 *
 * @see test.open.vincentf13.sdk.core.test.MysqlTest
 */
@MybatisTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MybatisTests {

  @Autowired private MybatisUserMapper mapper;
  @Autowired private JdbcTemplate jdbcTemplate;

  @DynamicPropertySource
  static void registerMysqlProperties(DynamicPropertyRegistry registry) {
    OpenMySqlTestContainer.register(registry);
  }

  @AfterAll
  static void cleanupSchema() {
    OpenMySqlTestContainer.clearAdminConnection();
  }

  @BeforeEach
  void resetSchema() {
    OpenMySqlTestContainer.prepareSchema();
    jdbcTemplate.execute("DROP TABLE IF EXISTS mybatis_users");
    jdbcTemplate.execute(
        "CREATE TABLE mybatis_users ("
            + "id BIGINT PRIMARY KEY AUTO_INCREMENT, "
            + "name VARCHAR(64) NOT NULL"
            + ")");
  }

  @Test
  void insertAndSelectRecord() {
    MybatisUser user = new MybatisUser(null, "MyBatis Tester");

    int affected = mapper.insert(user);
    assertThat(affected).isEqualTo(1);
    assertThat(user.getId()).isNotNull();

    MybatisUser selected = mapper.findById(user.getId());
    assertThat(selected).isNotNull();
    assertThat(selected.getName()).isEqualTo("MyBatis Tester");
    assertThat(mapper.countAll()).isEqualTo(1);
  }
}
