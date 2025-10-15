package test.open.vincentf13.common.core.test.Sample;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

/**
 * 以註解為基礎的 MyBatis Mapper，示範資料寫入與查詢。
 */
@Mapper
public interface MybatisUserMapper {

    @Insert("INSERT INTO mybatis_users(name) VALUES(#{name})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(MybatisUser user);

    @Select("SELECT id, name FROM mybatis_users WHERE id = #{id}")
    MybatisUser findById(Long id);

    @Select("SELECT COUNT(*) FROM mybatis_users")
    long countAll();
}
