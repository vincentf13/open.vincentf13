package test.open.vincentf13.common.core.test;

import open.vincentf13.common.core.test.OpenMySqlTestContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import test.open.vincentf13.common.core.test.Sample.MybatisUser;
import test.open.vincentf13.common.core.test.Sample.MybatisUserMapper;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

// MyBatis 切片測試：透過 Mapper 驗證 MySQL 臨時資料庫的增刪查
@org.mybatis.spring.boot.test.autoconfigure.MybatisTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MybatisTest {

    @DynamicPropertySource
    static void registerMysqlProperties(DynamicPropertyRegistry registry) {
        OpenMySqlTestContainer.register(registry);
    }

    private static final String TABLE_DDL = "CREATE TABLE mybatis_users (" +
            "id BIGINT PRIMARY KEY AUTO_INCREMENT, " +
            "name VARCHAR(64) NOT NULL" +
            ")";

    @Autowired
    private DataSource dataSource;

    @Autowired
    private MybatisUserMapper mapper;

    @BeforeEach
    void resetSchema() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP TABLE IF EXISTS mybatis_users");
        jdbcTemplate.execute(TABLE_DDL);
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
